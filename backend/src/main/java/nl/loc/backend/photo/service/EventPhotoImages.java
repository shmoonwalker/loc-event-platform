package nl.loc.backend.photo.service;

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
import nl.loc.backend.photo.model.PhotoVariant;
import nl.loc.backend.user.service.InvalidImageException;
import org.springframework.stereotype.Component;

/**
 * Turns an uploaded JPEG, PNG or WebP into two JPEGs. Re-encoding from pixels drops all metadata,
 * including location and camera data. The original file is never returned.
 */
@Component
public class EventPhotoImages {

    public static final long MAX_BYTES = 10L * 1024 * 1024;

    // a decoded pixel takes 4 bytes; this keeps one upload well below the heap
    private static final long MAX_PIXELS = 40_000_000L;
    private static final float JPEG_QUALITY = 0.85f;
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    public Derivatives derivatives(byte[] upload) {
        String format = detectFormat(upload);
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(upload))) {
            BufferedImage image = read(input, format);
            int orientation = "jpeg".equals(format) ? exifOrientation(upload) : 1;
            BufferedImage upright = upright(image, orientation);
            return new Derivatives(scale(upright, PhotoVariant.CARD), scale(upright, PhotoVariant.FULL));
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof InvalidImageException invalid) {
                throw invalid;
            }
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
            if ((long) width * height > MAX_PIXELS) {
                throw new InvalidImageException("The image has too many pixels");
            }
            // keep about two full-size pixels per output pixel, then scale down in memory
            int longest = Math.max(width, height);
            int step = Math.max(1, longest / (PhotoVariant.FULL.maxSide() * 2));
            ImageReadParam param = reader.getDefaultReadParam();
            param.setSourceSubsampling(step, step, 0, 0);
            return reader.read(0, param);
        } finally {
            reader.dispose();
        }
    }

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
            return 1;
        }
    }

    private static BufferedImage upright(BufferedImage source, int orientation) {
        int width = source.getWidth();
        int height = source.getHeight();
        boolean swap = orientation >= 5 && orientation <= 8;
        int destWidth = swap ? height : width;
        int destHeight = swap ? width : height;
        BufferedImage result = new BufferedImage(destWidth, destHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, destWidth, destHeight);
            graphics.drawImage(source, orientationTransform(orientation, width, height), null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    // EXIF orientation on a width x height image. Arguments are (m00, m10, m01, m11, m02, m12).
    private static AffineTransform orientationTransform(int orientation, int width, int height) {
        return switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, width, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, width, height);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, height);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, height, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, height, width);
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, width);
            default -> new AffineTransform();
        };
    }

    private static byte[] scale(BufferedImage source, PhotoVariant variant) {
        int width = source.getWidth();
        int height = source.getHeight();
        int longest = Math.max(width, height);
        if (longest <= variant.maxSide()) {
            return encode(source);
        }
        double ratio = variant.maxSide() / (double) longest;
        int destWidth = Math.max(1, (int) Math.round(width * ratio));
        int destHeight = Math.max(1, (int) Math.round(height * ratio));
        BufferedImage scaled = new BufferedImage(destWidth, destHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, destWidth, destHeight, null);
        } finally {
            graphics.dispose();
        }
        return encode(scaled);
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

    public record Derivatives(byte[] card, byte[] full) {
    }
}
