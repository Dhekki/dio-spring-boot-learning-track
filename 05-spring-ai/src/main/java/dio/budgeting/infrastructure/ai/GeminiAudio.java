package dio.budgeting.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

@Component
public class GeminiAudio {
    private static final String BASE = "https://generativelanguage.googleapis.com";
    private static final Set<String> TYPES = Set.of("audio/mp4", "audio/m4a", "audio/x-m4a", "audio/mpeg", "audio/mp3", "audio/wav", "audio/ogg", "audio/webm", "audio/flac", "audio/aac");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json;
    private final String key;

    public GeminiAudio(@Value("${GEMINI_API_KEY:}") String key) {
        this.json = new ObjectMapper();
        this.key = key;
    }

    public String transcribe(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Arquivo de áudio obrigatório");
        if (file.getSize() > 10_000_000) throw new IllegalArgumentException("Áudio excede 10 MB");
        String mime = file.getContentType();
        if (!TYPES.contains(mime)) throw new IllegalArgumentException("Formato de áudio não aceito");
        mime = providerMime(mime);
        requireKey();
        try {
            byte[] audio = file.getBytes();
            var start = HttpRequest.newBuilder(URI.create(BASE + "/upload/v1beta/files"))
                    .header("x-goog-api-key", key)
                    .header("X-Goog-Upload-Protocol", "resumable")
                    .header("X-Goog-Upload-Command", "start")
                    .header("X-Goog-Upload-Header-Content-Length", String.valueOf(audio.length))
                    .header("X-Goog-Upload-Header-Content-Type", mime)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"file\":{\"display_name\":\"audio\"}}"))
                    .timeout(Duration.ofSeconds(30)).build();
            var startResponse = http.send(start, HttpResponse.BodyHandlers.discarding());
            check(startResponse.statusCode());
            String uploadUrl = startResponse.headers().firstValue("x-goog-upload-url")
                    .orElseThrow(() -> new IllegalStateException("URL de upload ausente"));
            URI uploadUri = URI.create(uploadUrl);
            if (!"https".equals(uploadUri.getScheme()) || !"generativelanguage.googleapis.com".equals(uploadUri.getHost()))
                throw new IllegalStateException("URL de upload inválida");
            var upload = HttpRequest.newBuilder(uploadUri)
                    .header("X-Goog-Upload-Offset", "0")
                    .header("X-Goog-Upload-Command", "upload, finalize")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(audio))
                    .timeout(Duration.ofMinutes(2)).build();
            var uploaded = http.send(upload, HttpResponse.BodyHandlers.ofString());
            check(uploaded.statusCode());
            String uri = json.readTree(uploaded.body()).path("file").path("uri").asText();
            if (uri.isBlank()) throw new IllegalStateException("URI do arquivo ausente");
            JsonNode answer = interact(Map.of("model", "gemini-3.5-transcribe",
                    "input", new Object[]{Map.of("type", "audio", "uri", uri, "mime_type", mime)}));
            return transcript(answer);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao processar áudio");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Processamento de áudio interrompido");
        }
    }

    public byte[] speak(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Resposta de voz vazia");
        requireKey();
        JsonNode answer = interact(Map.of("model", "gemini-3.8-flash-lite-tts",
                "input", new Object[]{Map.of("type", "user_input", "content", new Object[]{Map.of("type", "text", "text", text)})},
                "response_format", Map.of("type", "audio"),
                "generation_config", Map.of("speech_config", new Object[]{Map.of("voice", "Kore")})));
        for (JsonNode step : answer.path("steps")) {
            if (!"model_output".equals(step.path("type").asText())) continue;
            for (JsonNode content : step.path("content")) {
                if ("audio".equals(content.path("type").asText())) return requireWav(content.path("data").asText());
            }
        }
        throw new IllegalStateException("Gemini não devolveu áudio");
    }

    public static byte[] requireWav(String base64) {
        byte[] bytes = Base64.getDecoder().decode(base64);
        if (bytes.length < 12 || bytes[0] != 'R' || bytes[1] != 'I' || bytes[2] != 'F' || bytes[3] != 'F'
                || bytes[8] != 'W' || bytes[9] != 'A' || bytes[10] != 'V' || bytes[11] != 'E')
            throw new IllegalStateException("Gemini não devolveu WAV");
        return bytes;
    }

    public static String providerMime(String mime) {
        return "audio/mp4".equals(mime) || "audio/x-m4a".equals(mime) ? "audio/m4a" : mime;
    }

    public static String transcript(JsonNode answer) {
        StringBuilder text = new StringBuilder();
        for (JsonNode step : answer.path("steps")) {
            if (!"model_output".equals(step.path("type").asText())) continue;
            for (JsonNode content : step.path("content")) {
                if ("text".equals(content.path("type").asText())) text.append(content.path("text").asText());
            }
        }
        if (text.isEmpty()) throw new IllegalArgumentException("Transcrição vazia");
        return text.toString();
    }

    private JsonNode interact(Object body) {
        requireKey();
        try {
            var request = HttpRequest.newBuilder(URI.create(BASE + "/v1beta/interactions"))
                    .header("x-goog-api-key", key).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .timeout(Duration.ofMinutes(2)).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            check(response.statusCode());
            return json.readTree(response.body());
        } catch (IOException e) {
            throw new IllegalStateException("Falha na API Gemini");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Chamada Gemini interrompida");
        }
    }

    private void requireKey() {
        if (key.isBlank()) throw new IllegalStateException("GEMINI_API_KEY não configurada");
    }

    private void check(int status) {
        if (status < 200 || status >= 300) throw new IllegalStateException("API Gemini respondeu HTTP " + status);
    }
}
