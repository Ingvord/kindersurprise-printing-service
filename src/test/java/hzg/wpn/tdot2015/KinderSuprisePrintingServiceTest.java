package hzg.wpn.tdot2015;

import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

import static org.junit.Assert.*;

public class KinderSuprisePrintingServiceTest {
    @Test
    public void testPrintImage() throws Exception {
        KinderSuprisePrintingService instance = new KinderSuprisePrintingService(null);

        instance.preparePrinterJob();

        BufferedImage img = ImageIO.read(KinderSuprisePrintingService.class.getResourceAsStream("/DESY_DAY_2015.jpg"));

        instance.printImage(img);
    }

    @Test
    public void testMerge() throws Exception {
        ImageIO.setUseCache(false);

        KinderSuprisePrintingService instance = new KinderSuprisePrintingService(null);

        instance.preparePrinterJob();

        BufferedImage certificate = ImageIO.read(KinderSuprisePrintingService.class.getResourceAsStream("/DESY_DAY_2015.jpg"));
        BufferedImage image = ImageIO.read(KinderSuprisePrintingService.class.getResourceAsStream("/test.jpg"));

        BufferedImage result = KinderSuprisePrintingService.mergeImageWithCertificate(image, certificate);

        instance.printImage(result);
    }
}