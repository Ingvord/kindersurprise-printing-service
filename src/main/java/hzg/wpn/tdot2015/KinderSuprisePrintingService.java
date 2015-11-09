package hzg.wpn.tdot2015;

import org.im4java.core.ConvertCmd;
import org.im4java.core.IM4JavaException;
import org.im4java.core.IMOperation;

import javax.imageio.ImageIO;
import javax.print.DocFlavor;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.print.*;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.FileAttribute;

/**
 * @author Igor Khokhriakov <igor.khokhriakov@hzg.de>
 * @since 06.11.2015
 */
public class KinderSuprisePrintingService implements Runnable, Printable {
    private static int dpi;
    private final Path inputFolder;
    private final Path dataFolder;
    private PrinterJob pj;
    private BufferedImage certificate;
    private BufferedImage image;

    public KinderSuprisePrintingService(Path inputFolder) throws IOException {
        this.inputFolder = inputFolder;

        this.dataFolder = Files.createTempDirectory(Paths.get(System.getenv("TEMP")), "KinderSuprisePrintingService_");

        this.certificate = ImageIO.read(KinderSuprisePrintingService.class.getResourceAsStream("/DESY_DAY_2015.jpg"));
    }


    public static void main(String[] args) throws Exception {
        Path p = Paths.get(args[0]);

        dpi = Integer.parseInt(args[1]);

        if(!Files.isDirectory(p) || !Files.exists(p)) throw new IllegalArgumentException(args[0] + " does not exists or it is not a folder!");

        KinderSuprisePrintingService instance = new KinderSuprisePrintingService(p);

//        instance.choosePrinter();

        instance.preparePrinterJob();



        instance.run();
    }

    private void choosePrinter(){
        PrintService pss[] = PrintServiceLookup.lookupPrintServices(DocFlavor.INPUT_STREAM.JPEG, null);
        if (pss.length == 0)
            throw new RuntimeException("No printer services available.");

        System.out.println("Available printers:");
        for (int i = 0; i < pss.length; i++) {
            PrintService ps = pss[i];
            System.out.print(i + ". ");
            System.out.println(ps.getName());
        }

        System.out.println("Enter printer number...");
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    }

    void preparePrinterJob() {
        pj = PrinterJob.getPrinterJob();

        PageFormat pf = pj.defaultPage();
        Paper paper = pf.getPaper();

        paper.setImageableArea(0, 0, paper.getWidth(), paper.getHeight());
        pf.setPaper(paper);

        PageFormat validatePage = pj.validatePage(pf);

        pj.setPrintable(this , validatePage);
    }

    public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {
        if (pageIndex != 0) {
            return NO_SUCH_PAGE;
        }

        graphics.translate(
                (int) pageFormat.getImageableX(),
                (int) pageFormat.getImageableY());

        int width = (int) Math.round(pageFormat.getImageableWidth());
        int height = (int) Math.round(pageFormat.getImageableHeight());

        graphics.drawImage(image, 0, 0, width, height, null);
        return PAGE_EXISTS;
    }

    @Override
    public void run() {
        try {
            WatchService watcher = FileSystems.getDefault().newWatchService();
            inputFolder.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
            dataFolder.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);

            Path lastPrinted = null;
            for (;;) {

                // wait for key to be signaled
                WatchKey key;
                try {
                    key = watcher.take();
                } catch (InterruptedException x) {
                    return;
                }

                for (WatchEvent<?> event: key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    // This key is registered only
                    // for ENTRY_CREATE events,
                    // but an OVERFLOW event can
                    // occur regardless if events
                    // are lost or discarded.
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    // The filename is the
                    // context of the event.
                    WatchEvent<Path> ev = (WatchEvent<Path>)event;
                    Path filename = ev.context();

                    // Verify that the new
                    //  file is a text file.
                    try {
                        // Resolve the filename against the directory.
                        // If the filename is "test" and the directory is "foo",
                        // the resolved name is "test/foo".
                        if(Files.exists(dataFolder.resolve(filename))
                                && Files.size(dataFolder.resolve(filename)) != 0) { // file has already been converted
                            lastPrinted = printFile(dataFolder.resolve(filename), lastPrinted);
                        } else {
                            if(!Files.exists(inputFolder.resolve(filename))
                                    || Files.size(inputFolder.resolve(filename)) == 0) continue;
                            // create command
                            ConvertCmd cmd = new ConvertCmd();
                            cmd.setSearchPath(Paths.get("C:\\Program Files\\ImageMagick-6.9.2-Q16").toString());

                            IMOperation op = new IMOperation();
                            op.addImage(inputFolder.resolve(filename).toAbsolutePath().toString());
                            op.colorspace("RGB");
                            op.addImage(dataFolder.resolve(filename).toAbsolutePath().toString());


                            cmd.run(op);
                        }
                    } catch (IOException|PrinterException|InterruptedException|IM4JavaException x) {
                        System.err.println(x);
                        continue;
                    }
                }

                // Reset the key -- this step is critical if you want to
                // receive further watch events.  If the key is no longer valid,
                // the directory is inaccessible so exit the loop.
                boolean valid = key.reset();
                if (!valid) {
                    break;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    Path printFile(Path filename, Path lastPrinted) throws IOException, PrinterException {
        String contentType = Files.probeContentType(filename);
        if (!contentType.equals("image/jpeg")) {
            System.err.format("New file '%s' is not a picture but %s.%n", filename, contentType);
            return lastPrinted;
        }
        //check size
        long size = Files.size(filename);
        System.out.println(filename.toString() + " size = " + size);
        if (size == 0) return lastPrinted;
        //Print
        Path absolutePath = filename.toAbsolutePath();
        if (absolutePath.equals(lastPrinted)) return lastPrinted;
        System.out.println("Printing " + absolutePath.toString());


        image = ImageIO.read(absolutePath.toFile());

        image = mergeImageWithCertificate(image, certificate);

        pj.print();

        return absolutePath;
    }

    public static BufferedImage mergeImageWithCertificate(BufferedImage image, BufferedImage certificate) {
        BufferedImage result = new BufferedImage(certificate.getWidth(), certificate.getHeight(), certificate.getType());

//        BufferedImage tmp =  new BufferedImage(image.getWidth(),image.getHeight(),certificate.getType());
//        tmp.getGraphics().drawImage(image,0,0,null);
//        tmp.getGraphics().dispose();

        Graphics2D g = result.createGraphics();

        g.drawImage(certificate, 0, 0, certificate.getWidth(), certificate.getHeight(), null);
        g.drawImage(image, (int) Conversions.cmsToPixel(4.2, dpi), (int) Conversions.cmsToPixel(4.6, dpi),
                (int) Conversions.cmsToPixel(12.5, dpi), (int) Conversions.cmsToPixel(12.5, dpi), null);
        g.dispose();

        return result;
    }

    public void printImage(BufferedImage img) throws PrinterException{
        this.image = img;
        pj.print();
    }
}
