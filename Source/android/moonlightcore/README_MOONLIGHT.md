# moonlightcore — núcleo de streaming do Moonlight/Artemis dentro do StormVR

Só o necessário para **conectar ao Sunshine/Apollo/GeForce e receber o vídeo/áudio**, sem a interface do Artemis:
- 29 arquivos Java do Artemis (pacote `com.limelight`): `NvConnection`, `NvHTTP`, `PairingManager`, `StreamConfiguration`,
  `MediaCodecDecoderRenderer` (vídeo, escreve numa `Surface`), `AndroidAudioRenderer`, `MoonBridge` (JNI) e dependências diretas.
  Lista exata do fechamento de dependências: veja `src/main/java`. + `AndroidCryptoProvider`.
- Código nativo: `src/main/jni` (moonlight-core, moonlight-common-c, libopus, openssl pré-compilado). Sem `evdev_reader` (modo root).
- NÃO incluídos (UI do Artemis): `Game`, `PcView`, `AppView`, configurações, descoberta mDNS, entrada por gamepad/mouse.

## Compilação e integração Workspace MR

O módulo está incluído em `settings.gradle.kts` e é dependência do app. A propriedade legada `stormvr.moonlight` não desativa o módulo nesta configuração.

Use NDK 27.0.12077973, Java 17 e Gradle 8.7 conforme o [README principal](../../../README.md). O código conecta `MoonlightSession` a uma SurfaceView no painel normal e a uma SurfaceTexture OES consumida na thread OpenGL na Sala VR. A renderização consome o vídeo uma vez por frame, antes de desenhar os dois olhos.

O Workspace mantém apenas uma conexão nativa Moonlight. A mudança de superfície encerra o produtor anterior antes de liberar a superfície antiga. Reconexões e callbacks são invalidados por geração ao sair. H.264 é fixado no Workspace; o painel normal mantém a seleção automática de codecs.

Consulte o [relatório de testes](../../../docs/TEST_RESULTS.md). Compilar não comprova ausência de congelamentos em aparelhos; são necessários testes com Sunshine, MediaCodec, mãos ativas e perda de contexto EGL.
