# Orçamento por voz com Spring AI e Gemini

Este módulo da trilha DIO registra e consulta gastos por REST ou comando de voz. O áudio enviado é transcrito pela Gemini API; o chat escolhe uma das ferramentas da aplicação; o caso de uso acessa o repositório JPA; a resposta é sintetizada em áudio WAV. A ferramenta de registro passa pela mesma validação usada pelo REST.

## O que mudou

- Migração de chat e tool calling da OpenAI para Google GenAI no Spring AI 2.0.0-M4, usando `gemini-3.5-flash-lite`.
- Transcrição e fala via Files API e Interactions API do Gemini, pois esse marco do Spring AI não fornece os dois modelos de áudio usados aqui.
- Valor de entrada e de armazenamento em **centavos inteiros**; valor de saída em **reais decimais**. `8000` entra como R$ 80,00 e sai como `80.00`.
- Descrição obrigatória e aparada, valor positivo e categoria obrigatória antes de salvar. Dados inválidos via REST retornam HTTP 400.
- O áudio de resposta é `audio/wav`, anexo `audio.wav`. O código verifica o cabeçalho RIFF/WAVE recebido antes de responder.

## Requisitos e execução

Java 25, Docker com Compose para o MySQL local, e chave da Gemini Developer API com acesso aos modelos configurados. O `compose.yml` usa credenciais **somente para desenvolvimento** e expõe o MySQL na porta 3307. O banco é necessário para executar a aplicação; os testes locais o dispensam.

Configure `GEMINI_API_KEY` no ambiente local, sem colocá-la em arquivo rastreado. Exemplo de comandos Bash, a partir deste diretório:

```bash
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk
export PATH="$JAVA_HOME/bin:$PATH"
export GEMINI_API_KEY='<insira a chave apenas no seu terminal local>'
docker compose up -d
./gradlew bootRun
```

Se a conexão do Compose não for detectada automaticamente, configure `spring.datasource.url`, `spring.datasource.username` e `spring.datasource.password` no ambiente local conforme seu MySQL. A chave é necessária somente para o fluxo de IA. A disponibilidade da cota gratuita depende da conta, região, limites e modelos vigentes; confira a [tabela oficial](https://ai.google.dev/gemini-api/docs/pricing). Use áudios fictícios: a camada gratuita pode usar conteúdo para melhorar os produtos do provedor.

## API

Categorias: `GROCERIES`, `PHARMA`, `AUTO`. O campo `amount` do POST está em centavos. O `amount` da resposta está em reais.

```bash
curl -i -X POST http://localhost:8080/transactions \
  -H 'Content-Type: application/json' \
  -d '{"description":"Mercado","category":"GROCERIES","amount":8000}'
```

Resposta HTTP 201, com ID variável:

```json
{"id":"<uuid>","category":"GROCERIES","description":"Mercado","amount":80.00}
```

```bash
curl http://localhost:8080/transactions/GROCERIES
curl -X POST http://localhost:8080/transactions/ai \
  -F 'file=@src/test/resources/audio/recording-1.m4a;type=audio/m4a' \
  -o audio.wav
```

O upload aceita M4A, MP3, WAV, OGG, WebM, FLAC e AAC até 10 MB. Arquivo vazio, MIME não aceito, transcrição vazia ou dados financeiros inválidos são rejeitados; os erros de entrada REST retornam 400. Problemas de provedor podem interromper o fluxo. Uma gravação pode ser concluída antes de uma falha posterior na geração de fala; consulte o endpoint REST antes de reenviar o mesmo comando.

## Testes e limites da evidência

```bash
./gradlew test
```

Os testes locais cobrem conversão monetária, validação antes da persistência, contratos HTTP, contexto Spring sem banco e assinatura WAV. `GeminiIntegrationIT` só roda quando `GEMINI_API_KEY` está presente; ele exercita transcrição, fala e tool calling com o provedor. Sem essa variável, os dois testes de integração são ignorados.

Em 18/10/2026, a suíte completa com Java 25 e uma chave Gemini configurada no ambiente passou com **12 testes, nenhuma falha e nenhum teste ignorado**. Também executei a aplicação com MySQL local e enviei `recording-1.m4a` a `POST /transactions/ai`: resposta HTTP 200, `audio/wav`, 472.674 bytes, WAV PCM mono de 24 kHz com 9,72 s. A consulta REST posterior mostrou uma transação fictícia na categoria `PHARMA` com `amount: 80.00`. Esse teste confirma o fluxo completo nessa execução; resultados do modelo podem variar em novas chamadas. Os testes não verificam uso em produção, limites de cota ou reprodução em todos os clientes.

Este é um projeto educacional: não há autenticação nem garantia de entrega exatamente uma vez. O aprendizado principal foi manter a regra financeira no caso de uso, deixando a IA escolher operações sem ganhar acesso direto à persistência, e distinguir teste determinístico da chamada externa.

Documentação: [Spring AI Google GenAI](https://docs.spring.io/spring-ai/reference/api/chat/google-genai-chat.html), [transcrição Gemini](https://ai.google.dev/gemini-api/docs/transcribe), [fala Gemini](https://ai.google.dev/gemini-api/docs/speech-generation).
