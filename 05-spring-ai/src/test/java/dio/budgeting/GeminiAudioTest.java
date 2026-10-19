package dio.budgeting;

import dio.budgeting.infrastructure.ai.GeminiAudio;
import org.junit.jupiter.api.Test;
import java.util.Base64;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;

class GeminiAudioTest {
    @Test
    void acceptsWavAndRejectsOtherAudio() {
        byte[] wav = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'};
        assertThat(GeminiAudio.requireWav(Base64.getEncoder().encodeToString(wav))).isEqualTo(wav);
        assertThatThrownBy(() -> GeminiAudio.requireWav(Base64.getEncoder().encodeToString(new byte[]{1, 2, 3})))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void extractsTranscriptFromInteractionSteps() throws Exception {
        var response = new ObjectMapper().readTree("{\"steps\":[{\"type\":\"model_output\",\"content\":[{\"type\":\"text\",\"text\":\"Comprei por 80 reais\"}]}]}");
        assertThat(GeminiAudio.transcript(response)).isEqualTo("Comprei por 80 reais");
    }

    @Test void normalizesCommonM4aMime() {
        assertThat(GeminiAudio.providerMime("audio/mp4")).isEqualTo("audio/m4a");
        assertThat(GeminiAudio.providerMime("audio/x-m4a")).isEqualTo("audio/m4a");
    }
}
