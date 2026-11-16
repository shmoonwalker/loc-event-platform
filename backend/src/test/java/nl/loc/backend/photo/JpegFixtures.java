package nl.loc.backend.photo;

import com.twelvemonkeys.imageio.metadata.Directory;
import com.twelvemonkeys.imageio.metadata.jpeg.JPEG;
import com.twelvemonkeys.imageio.metadata.jpeg.JPEGSegment;
import com.twelvemonkeys.imageio.metadata.jpeg.JPEGSegmentUtil;
import com.twelvemonkeys.imageio.metadata.tiff.TIFFReader;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import nl.loc.backend.photo.service.EventPhotoImages;

/** Small images for photo tests. The GPS fixture is a real Exif block, so a passing test is not vacuous. */
public final class JpegFixtures {

    private static final int GPS_POINTER = 0x8825;

    // 1x1 lossy WebP
    private static final byte[] WEBP = Base64.getDecoder().decode(
            "UklGRiQAAABXRUJQVlA4IBgAAAAwAQCdASoBAAEAAwA0JaQAA3AA/vuUAAA=");

    private JpegFixtures() {
    }

    public static byte[] jpeg(int width, int height) throws IOException {
        return jpeg(width, height, false);
    }

    public static byte[] jpegWithGps(int width, int height) throws IOException {
        return jpeg(width, height, true);
    }

    public static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(0, 0, 0, 0));
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(Color.BLUE);
            graphics.fillRect(0, 0, Math.max(1, width / 2), height);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    public static byte[] webp() {
        return WEBP.clone();
    }

    public static byte[] text() {
        return "not an image".getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] tooBig() {
        byte[] bytes = new byte[(int) EventPhotoImages.MAX_BYTES + 1];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;
        return bytes;
    }

    public static boolean hasGps(byte[] jpeg) {
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(jpeg))) {
            List<JPEGSegment> segments = JPEGSegmentUtil.readSegments(input, JPEG.APP1, "Exif");
            if (segments.isEmpty()) {
                return false;
            }
            InputStream data = segments.getFirst().data();
            data.read();
            Directory exif = new TIFFReader().read(new MemoryCacheImageInputStream(data));
            return exif.getEntryById(GPS_POINTER) != null;
        } catch (IOException | RuntimeException ex) {
            return false;
        }
    }

    private static byte[] jpeg(int width, int height, boolean gps) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.RED);
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(Color.BLUE);
            graphics.fillRect(0, 0, Math.max(1, width / 5), height);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", out);
        byte[] jpeg = out.toByteArray();
        if (!gps) {
            return jpeg;
        }
        byte[] app1 = exifApp1();
        byte[] combined = new byte[jpeg.length + app1.length];
        combined[0] = jpeg[0];
        combined[1] = jpeg[1];
        System.arraycopy(app1, 0, combined, 2, app1.length);
        System.arraycopy(jpeg, 2, combined, 2 + app1.length, jpeg.length - 2);
        return combined;
    }

    // APP1 Exif whose root IFD points at a GPS IFD (tag 0x8825) for Amsterdam-ish coordinates.
    private static byte[] exifApp1() {
        ByteBuffer tiff = ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN);
        tiff.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8);
        tiff.putShort((short) 1);
        entry(tiff, GPS_POINTER, 4, 1, 26);
        tiff.putInt(0);
        tiff.putShort((short) 4);
        entry(tiff, 1, 2, 2, asciiValue('N'));
        entry(tiff, 2, 5, 3, 80);
        entry(tiff, 3, 2, 2, asciiValue('E'));
        entry(tiff, 4, 5, 3, 104);
        tiff.putInt(0);
        rational(tiff, 52, 1);
        rational(tiff, 22, 1);
        rational(tiff, 0, 1);
        rational(tiff, 4, 1);
        rational(tiff, 53, 1);
        rational(tiff, 0, 1);
        byte[] tiffBytes = tiff.array();
        byte[] payload = new byte[6 + tiffBytes.length];
        payload[0] = 'E';
        payload[1] = 'x';
        payload[2] = 'i';
        payload[3] = 'f';
        System.arraycopy(tiffBytes, 0, payload, 6, tiffBytes.length);
        byte[] segment = new byte[4 + payload.length];
        segment[0] = (byte) 0xFF;
        segment[1] = (byte) 0xE1;
        int length = payload.length + 2;
        segment[2] = (byte) ((length >> 8) & 0xFF);
        segment[3] = (byte) (length & 0xFF);
        System.arraycopy(payload, 0, segment, 4, payload.length);
        return segment;
    }

    private static void entry(ByteBuffer buffer, int tag, int type, int count, int value) {
        buffer.putShort((short) tag);
        buffer.putShort((short) type);
        buffer.putInt(count);
        buffer.putInt(value);
    }

    private static int asciiValue(char letter) {
        return letter & 0xFF;
    }

    private static void rational(ByteBuffer buffer, int numerator, int denominator) {
        buffer.putInt(numerator);
        buffer.putInt(denominator);
    }
}
