/*
 * Copyright (c) 2004, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

/*
 * $Id: AttachmentHelper.java,v 1.1 2005/10/07 22:46:08 kk122374 Exp $
 */
package testutil;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.imageio.ImageWriter;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.awt.image.PixelGrabber;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Iterator;

import org.w3c.dom.Node;

public class AttachmentHelper {
    public static boolean compareSource(Source src, Source target) throws TransformerException {
        Transformer t1 = TransformerFactory.newInstance().newTransformer();
        Transformer t2 = TransformerFactory.newInstance().newTransformer();
        DOMResult srcResult = new DOMResult();
        DOMResult tarResult = new DOMResult();
        t1.transform(src, srcResult);
        Node n1 = srcResult.getNode();
        t2.transform(target, tarResult);
        Node n2 = tarResult.getNode();
        return n1.getNodeName().equals(n2.getNodeName());
    }

//    public static boolean compareStreamSource(Source src1, Source src2) throws Exception {
//        if (src1 == null || src2 == null) {
//            System.out.println("compareStreamSource - src1 or src2 null!");
//            return false;
//        }
//        InputStream is1 = null;
//        InputStream is2 = null;
//        if(src1 instanceof StreamSource)
//            is1 = ((StreamSource)src1).getInputStream();
//        if(src2 instanceof StreamSource)
//            is2 = ((StreamSource)src2).getInputStream();
//        if ((is1 == null) || (is2 == null)) {
//            System.out.println("InputStream of - src1 or src2 null!");
//            return false;
//        }
//        return Arrays.equals(ASCIIUtility.getBytes(is1), ASCIIUtility.getBytes(is2));
//    }

    public static byte[] getImageBytes(Image image, String type) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        BufferedImage bufImage = convertToBufferedImage(image);
        ImageWriter writer = null;
        Iterator i = ImageIO.getImageWritersByMIMEType(type);
        if (i.hasNext()) {
            writer = (ImageWriter)i.next();
        }
        if (writer != null) {
            ImageOutputStream stream = null;
            stream = ImageIO.createImageOutputStream(baos);
            writer.setOutput(stream);
            writer.write(bufImage);
            stream.close();
            return baos.toByteArray();
        }
        return null;
    }

    private static BufferedImage convertToBufferedImage(Image image) throws IOException {
        if (image instanceof BufferedImage) {
            return (BufferedImage)image;

        } else {
            MediaTracker tracker = new MediaTracker(null/*not sure how this is used*/);
            tracker.addImage(image, 0);
            try {
                tracker.waitForAll();
            } catch (InterruptedException e) {
                throw new IOException(e.getMessage());
            }
            BufferedImage bufImage = new BufferedImage(
                    image.getWidth(null),
                    image.getHeight(null),
                    BufferedImage.TYPE_INT_RGB);

            Graphics g = bufImage.createGraphics();
            g.drawImage(image, 0, 0, null);
            return bufImage;
        }
    }

    public static boolean compareImages(Image image1, Image image2) throws IOException {
        if (image1 == null || image2 == null)
            return false;

        boolean matched = false;
        Rectangle rect = new Rectangle(0, 0, convertToBufferedImage(image1).getWidth(), convertToBufferedImage(image1).getHeight());
        Iterator iter1 = handlePixels(image1, rect);
        Iterator iter2 = handlePixels(image2, rect);

        while (iter1.hasNext() && iter2.hasNext()) {
            Pixel pixel = (Pixel) iter1.next();
            matched = pixel.equals((Pixel) iter2.next());
        }
        return matched;
    }

    private static Iterator handlePixels(Image img, Rectangle rect) {
        int x = rect.x;
        int y = rect.y;
        int w = rect.width;
        int h = rect.height;

        int[] pixels = new int[w * h];
        PixelGrabber pg = new PixelGrabber(img, x, y, w, h, pixels, 0, w);
        try {
            pg.grabPixels();
        } catch (InterruptedException e) {
            System.err.println("interrupted waiting for pixels!");
            return null;
        }
        if ((pg.getStatus() & ImageObserver.ABORT) != 0) {
            System.err.println("image fetch aborted or errored");
            return null;
        }
        ArrayList tmpList = new ArrayList();
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                tmpList.add(handleSinglePixel(x + i, y + j, pixels[j * w + i]));
            }
        }
        return tmpList.iterator();
    }

    private static Pixel handleSinglePixel(int x, int y, int pixel) {
        int alpha = (pixel >> 24) & 0xff;
        int red = (pixel >> 16) & 0xff;
        int green = (pixel >> 8) & 0xff;
        int blue = (pixel) & 0xff;
        return new Pixel(alpha, red, green, blue);
    }

    private static class Pixel {
        private int a;
        private int r;
        private int g;
        private int b;

        Pixel(int a, int r, int g, int b) {
            this.a = a;
            this.r = r;
            this.g = g;
            this.b = b;
        }

        protected boolean equals(Pixel p) {
            return (p.a == a && p.r == r && p.g == g && p.b == b);
        }
    }
}


