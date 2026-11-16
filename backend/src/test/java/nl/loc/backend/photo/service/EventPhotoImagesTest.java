package nl.loc.backend.photo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import nl.loc.backend.photo.JpegFixtures;
import nl.loc.backend.user.service.InvalidImageException;
import org.junit.jupiter.api.Test;

class EventPhotoImagesTest {

    private final EventPhotoImages images = new EventPhotoImages();

    @Test
    void reEncodingDropsGpsAndKeepsTheLongestSideAtTheTarget() throws Exception {
        byte[] upload = JpegFixtures.jpegWithGps(2000, 1000);
        assertThat(JpegFixtures.hasGps(upload)).isTrue();

        EventPhotoImages.Derivatives derivatives = images.derivatives(upload);

        assertThat(JpegFixtures.hasGps(derivatives.card())).isFalse();
        assertThat(JpegFixtures.hasGps(derivatives.full())).isFalse();
        assertThat(size(derivatives.card())).isEqualTo(new int[] {640, 320});
        assertThat(size(derivatives.full())).isEqualTo(new int[] {1600, 800});
    }

    @Test
    void aSmallImageIsNotEnlarged() throws Exception {
        EventPhotoImages.Derivatives derivatives = images.derivatives(JpegFixtures.jpeg(20, 10));

        assertThat(size(derivatives.card())).isEqualTo(new int[] {20, 10});
        assertThat(size(derivatives.full())).isEqualTo(new int[] {20, 10});
    }

    @Test
    void pngAndWebpAreAccepted() throws Exception {
        assertThat(size(images.derivatives(JpegFixtures.png(30, 20)).card())).isEqualTo(new int[] {30, 20});
        assertThat(size(images.derivatives(JpegFixtures.webp()).full())).isEqualTo(new int[] {1, 1});
    }

    @Test
    void otherFilesAreRejected() {
        assertThatThrownBy(() -> images.derivatives(JpegFixtures.text()))
                .isInstanceOf(InvalidImageException.class)
                .hasMessage("Only JPEG, PNG and WebP images are accepted");
    }

    private static int[] size(byte[] jpeg) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(jpeg));
        return new int[] {image.getWidth(), image.getHeight()};
    }
}
