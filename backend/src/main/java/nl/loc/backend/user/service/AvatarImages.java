package nl.loc.backend.user.service;

import com.twelvemonkeys.imageio.metadata.Directory;
import com.twelvemonkeys.imageio.metadata.Entry;
import com.twelvemonkeys.imageio.metadata.jpeg.JPEG;
import com.twelvemonkeys.imageio.metadata.jpeg.JPEGSegment;
import com.twelvemonkeys.imageio.metadata.jpeg.JPEGSegmentUtil;
import com.twelvemonkeys.imageio.metadata.tiff.TIFF;
import com.twelvemonkeys.imageio.metadata.tiff.TIFFReader;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.stereotype.Component;

/**
 * Turns an uploaded JPEG, PNG or WebP into a square JPEG. Re-encoding from pixels drops all metadata,
 * including location and camera data.
 */
@Component
public class AvatarImages {

    public static final int SIZE = 512;

    // a decoded pixel takes 4 bytes; this keeps one upload well below the heap
    private static final long MAX_PIXELS = 40_000_000L;
    private static final float JPEG_QUALITY = 0.85f;
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    public byte[] toAvatarJpeg(byte[] upload) {
        String format = detectFormat(upload);
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(upload))) {
            BufferedImage image = read(input, format);
            int orientation = "jpeg".equals(format) ? exifOrientation(upload) : 1;
            return encode(squareAndOrient(image, orientation));
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof InvalidImageException invalid) {
                throw invalid;
            }
            // decoders throw all kinds of exceptions on broken files
            throw new InvalidImageException("The file could not be read as an image");
        }
    }

    // by content, never by file name or the client's content type
    private static String detectFormat(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "jpeg";
        }
        if (bytes.length >= 8 && Arrays.equals(bytes, 0, 8, PNG_SIGNATURE, 0, 8)) {
            return "png";
        }
        if (bytes.length >= 12 && ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP")) {
            return "webp";
        }
        throw new InvalidImageException("Only JPEG, PNG and WebP images are accepted");
    }

    private static boolean ascii(byte[] bytes, int offset, String text) {
        for (int i = 0; i < text.length(); i++) {
            if (bytes[offset + i] != text.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static BufferedImage read(ImageInputStream input, String format) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format);
        if (!readers.hasNext()) {
            throw new IllegalStateException("No ImageIO reader for " + format);
        }
        ImageReader reader = readers.next();
        try {
            reader.setInput(input, true, true);
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            // checked before decoding, so a small file that claims huge dimensions costs nothing
            if ((long) width * height > MAX_PIXELS) {
                throw new InvalidImageException("The image has too many pixels");
            }
            // skip pixels while decoding big photos; the short side still ends up at least 2 * SIZE
            int step = Math.max(1, Math.min(width, height) / (SIZE * 2));
            ImageReadParam param = reader.getDefaultReadParam();
            param.setSourceSubsampling(step, step, 0, 0);
            return reader.read(0, param);
        } finally {
            reader.dispose();
        }
    }

    // phones store photos sideways and say how to turn them in the EXIF orientation tag
    private static int exifOrientation(byte[] jpeg) {
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(jpeg))) {
            List<JPEGSegment> segments = JPEGSegmentUtil.readSegments(input, JPEG.APP1, "Exif");
            if (segments.isEmpty()) {
                return 1;
            }
            InputStream data = segments.getFirst().data();
            // "Exif" is followed by two zero bytes; the identifier and the first one are already skipped
            data.read();
            Directory exif = new TIFFReader().read(new MemoryCacheImageInputStream(data));
            Entry entry = exif.getEntryById(TIFF.TAG_ORIENTATION);
            return entry == null ? 1 : ((Number) entry.getValue()).intValue();
        } catch (IOException | RuntimeException ex) {
            // a broken EXIF block must not reject an image that decodes fine
            return 1;
        }
    }

    private static BufferedImage squareAndOrient(BufferedImage source, int orientation) {
        int side = Math.min(source.getWidth(), source.getHeight());
        int x = (source.getWidth() - side) / 2;
        int y = (source.getHeight() - side) / 2;
        BufferedImage result = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = result.createGraphics();
        try {
            // JPEG has no transparency; transparent PNG and WebP pixels become white
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, SIZE, SIZE);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            // the centre square stays the centre square after turning, so cropping first is safe
            graphics.transform(orientationTransform(orientation));
            graphics.drawImage(source, 0, 0, SIZE, SIZE, x, y, x + side, y + side, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    // EXIF orientation values 2-8 on a SIZE x SIZE square; arguments are (m00, m10, m01, m11, m02, m12)
    private static AffineTransform orientationTransform(int orientation) {
        return switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, SIZE, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, SIZE, SIZE);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, SIZE);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, SIZE, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, SIZE, SIZE);
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, SIZE);
            default -> new AffineTransform();
        };
    }

    private static byte[] encode(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream output = new MemoryCacheImageOutputStream(bytes)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.setOutput(output);
            // no metadata passed in, so none is written
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
