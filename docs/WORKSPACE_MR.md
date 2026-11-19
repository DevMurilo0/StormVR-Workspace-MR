# StormVR Workspace MR — implementação experimental

**Versão experimental v0.1.0-alpha. Consulte o [relatório de testes](TEST_RESULTS.md) para o resultado da compilação. Não há validação em aparelho nesta sessão.**

Repositório reutilizado: `/home/murilo/StormVR-teste`, retomado no commit `dbc19ad`, branch `feature/workspace-mr`. O histórico completo original foi recuperado. Caches e builds herdados foram retirados da árvore atual, mantendo os commits antigos. O diretório preexistente `Source/linux_droiddeck/dist/` foi preservado localmente e ignorado.

## O que foi alterado

- Identidade debug `com.stormvr.workspace.mr`, rótulo **StormVR Workspace MR**, sem substituir o app original.
- Saída Moonlight diretamente para uma SurfaceTexture OES, separada da superfície do display virtual da interface. `updateTexImage` ocorre na thread GL, antes de desenhar ambos os olhos; matriz de textura respeitada. SurfaceView continua disponível no painel normal. Contador de frames nos diagnósticos.
- Reconexão Moonlight após término, com intervalo crescente até 15 segundos; cancelamento ao sair. Status volta a ser composto pela interface durante desconexão. Sessões antigas são invalidadas por geração.
- Reanexação da superfície do display virtual ao retomar e preservação do contexto EGL. Pausa interrompe streaming, câmera e interação. Superfícies do decodificador anterior são liberadas depois de parar seu proprietário.
- `WorkspaceManager`, `VirtualScreen`, `WorkspaceDisplays`, `WorkspaceInteraction`, `WorkspacePanel`: até três telas totais, sendo a principal original mais duas independentes. Cada adicional possui UUID, nome, URL, status de página, geometria própria, visibilidade, bloqueio e seleção.
- Cada adicional possui VirtualDisplay, Presentation, WebView e textura próprios. Conteúdo diferente depende da URL/fonte escolhida; não há duplicação da textura Moonlight.
- Presets simples, triplo, panorâmico e livre; salvar/carregar layout local versionado. Distância, tamanho, órbita horizontal/vertical, rotação yaw, curvatura, bloqueio e opacidade pelo painel. Painel acessível antes do VR e pela Home interna da telona.
- Raios de entrada já filtrados pelo StormVR manipulam telas adicionais: pinça/toque no conteúdo, pinça na faixa superior para mover, canto para redimensionar. Duas mãos opcionais ajustam largura/distância. O modo inicial continua com uma mão. Sensibilidade e suavização ajustáveis; histerese, filtros e recuperação existentes do HandTracker foram preservados.
- Perfis Economia (720p20), Equilibrado (720p30) e Qualidade (1080p30) para Moonlight, sempre H.264. Perfil aplica na conexão seguinte. Captura Linux alternativa tem seu próprio limite de FPS.
- Páginas do serviço Linux suspendem a busca de frames em telas ocultas ou atrás do usuário. Texturas continuam sendo drenadas para não bloquear produtores.
- Camera2 como fallback quando ARCore não está disponível: câmera traseira monocular, orientação por sensor/display e rotação IMU. **Sem rastreamento de mãos nesse fallback.** Não fornece 6DoF. O modo MR desativa deslocamentos fictícios por IMU/inclinação/barômetro.

## Compilação Android

Requisitos confirmados nos arquivos: AGP 8.5.2, Gradle 8.7, Kotlin 1.9.24, Java 17; app SDK 34, biblioteca Moonlight SDK 35; NDK 27.0.12077973, ARM64 e ARMv7. Moonlight inclui bibliotecas nativas de terceiros e continua habilitado.

O upstream não contém o wrapper completo. Foi gerado o **wrapper oficial Gradle 8.7**, incluindo JAR, scripts Unix/Windows e SHA-256 fixado. O bootstrap baixa e verifica a distribuição e configura os caminhos no `/home`. O auxiliar `tools/workspace/build-android.sh` usa diretamente a distribuição local verificada.

```bash
cd /home/murilo/StormVR-teste
python tools/workspace/bootstrap-android.py --accept-licenses
source "$HOME/Android/stormvr/environment.sh"
bash tools/workspace/build-android.sh :app:testDebugUnitTest :app:assembleDebug
```

O argumento `--accept-licenses` aceita os termos apresentados pelo sdkmanager. Reserve ao menos 10 GiB para toolchain/cache e mais espaço para dependências e builds. Sem sudo. Para outro local, use `STORMVR_TOOLCHAIN=/caminho/no/home` tanto no bootstrap quanto no lançador.

O bootstrap substitui somente a configuração local de SDK em `local.properties`, ignorando o caminho Windows original. Esse arquivo fica fora do ZIP e de novos commits. O JDK 17 vem do Adoptium, com checksum da API; os command-line tools vêm do índice oficial Google, com checksum do índice. Não reutilize downloads parciais como ferramentas instaladas.

Caminho de saída do build:

`Source/android/app/build/outputs/apk/debug/app-debug.apk`

Instalação, após compilação bem-sucedida:

```bash
"$ANDROID_HOME/platform-tools/adb" install -r Source/android/app/build/outputs/apk/debug/app-debug.apk
```

Não execute uninstall do app original. O debug usa outra identidade e outro armazenamento: será necessário parear o PC novamente. Não se copiam certificados de pareamento do app antigo.

### Recuperação do ambiente

A instalação anterior do NDK continha somente `.installer`, sem `source.properties`. A recuperação usa o arquivo oficial `https://dl.google.com/android/repository/android-ndk-r27-linux.zip`, cujo SHA-1 publicado pelo índice Google é `5e5cd517bdb98d7e0faf2c494a3041291e71bdcc`. A instalação é aceita somente após checksum, CRC do ZIP, conferência da revisão e execução do Clang. O bootstrap também detecta e remove somente a instalação incompleta da versão fixada antes de reinstalar.

SDK/NDK, JDK 17, Gradle 8.7, downloads e caches ficam em `/home/murilo/Android/stormvr`. `local.properties` aponta para esse SDK e permanece fora do Git. As plataformas 34/35 e platform-tools também são verificadas contra os checksums oficiais. Os logs locais ficam no diretório da ferramenta; o resultado final está no relatório de testes.

## Conectar Sunshine/Moonlight

1. Mantenha o Sunshine que já funciona no computador e ambos os dispositivos na mesma LAN.
2. Abra **Moonlight** no início do StormVR modificado, adicione o endereço do PC, solicite pareamento e confirme o PIN na interface do Sunshine.
3. Selecione 1280×720, 30 FPS, aproximadamente 6 Mbps inicialmente (ou o perfil Equilibrado no painel).
4. Entre no Workspace MR, feche o painel inicial se ainda estiver aberto, abra a Home da telona e escolha Moonlight → Desktop.
5. Ative os diagnósticos pelo menu e observe se o contador de frames cresce continuamente. Isso ainda precisa de validação real com o decodificador do celular.
6. Para uma segunda fonte, adicione tela no Workspace e informe outra fonte HTTPS. Reabrir Moonlight não cria um monitor Linux independente.

A implementação mantém **uma sessão Moonlight**. O núcleo nativo tem estado global e não foi tratado como suporte a sessões simultâneas. Sunshine documenta compartilhamento da mesma sessão entre clientes; não é um gerenciador de streams independentes por monitor. [Documentação Sunshine](https://docs.lizardbyte.dev/projects/sunshine/v0.23.0/about/advanced_usage.html).

## Fontes Linux independentes por portal/PipeWire

O serviço `tools/workspace/server.py` solicita captura pelo portal ScreenCast, permitindo seleção múltipla. O usuário escolhe explicitamente os monitores na janela do KDE; fontes virtuais são solicitadas somente quando anunciadas pelo portal. Cada fonte autorizada tem pipeline e endpoint próprios. Ao encerrar o serviço, a sessão é fechada. **A captura do desktop real não foi testada nesta sessão**, pois depende da escolha interativa de fontes.

Este KDE anunciou portal v6 e máscara de fontes 7 (monitor, janela e monitor virtual). Isso comprova a capacidade anunciada, não a criação ou estabilidade de dois monitores virtuais simultâneos. `krfb-virtualmonitor` não está instalado. Preferiu-se o portal existente a introduzir um servidor VNC e novas dependências. O portal preserva a decisão do usuário e pode oferecer um novo monitor virtual; a opção concreta depende da interface do KDE. [Especificação ScreenCast](https://flatpak.github.io/xdg-desktop-portal/docs/doc-org.freedesktop.portal.ScreenCast.html).

Dependências verificadas neste host: Python, python-dbus, PyGObject, GStreamer com `pipewiresrc`, `videoconvert`, `videoscale`, `videorate`, `jpegenc`, `appsink`; OpenSSL e kscreen-doctor. Para instalar em outro Arch, confira antes espaço/pacotes:

```bash
pacman -Q python python-dbus python-gobject gstreamer gst-plugins-base gst-plugins-good gst-plugin-pipewire openssl kscreen
# Instale apenas os ausentes com o gerenciador do sistema, se necessário.
bash tools/workspace/check-host.sh
bash tools/workspace/install-service.sh
```

Configuração explícita de TLS e acesso:

```bash
bash tools/workspace/setup-tls.sh 192.168.1.20
python tools/workspace/server.py --bind 192.168.1.20 \
  --cert "$HOME/.config/stormvr-workspace/cert.pem" \
  --key "$HOME/.config/stormvr-workspace/key.pem" \
  --token-file "$HOME/.config/stormvr-workspace/token" --fps 10
```

Substitua o IP pelo endereço privado real do computador. Não abra a porta no roteador; se houver firewall local, permita TCP 8443 somente da LAN/dispositivo necessário. O serviço exige endereço privado explícito e não aceita `0.0.0.0`.

Instale **somente** `stormvr-lan.crt` como CA do usuário no Android, após comparar a impressão SHA-256 exibida pelo script. Nunca transfira `key.pem`. O APK debug confia nas CAs de usuário; não foi implementado ignorar erros TLS. O certificado autoassinado é adequado a desenvolvimento local, não distribuição pública. A configuração de rede original para outros recursos do StormVR foi preservada.

Leia o token localmente e configure a fonte da tela adicional como:

`https://192.168.1.20:8443/#TOKEN_LOCAL`

A página lista os streams consentidos; selecione um. Em outra tela, selecione outro ID. É possível configurar diretamente `https://192.168.1.20:8443/view/ID#TOKEN_LOCAL`. O fragmento não é transmitido na URL HTTP: JavaScript envia o token em cabeçalho Authorization. Não compartilhe layouts contendo o token; layouts são privados ao app.

Endpoints autenticados:

- `GET /sources`: fontes consentidas, metadados do portal, estado e sequência.
- `GET /monitors`: inventário KScreen, resolução e refresh; a classificação por nome é heurística e marcada como tal.
- `GET /frame/ID`: último JPEG recente, 503 se estiver desatualizado, 404 para fonte não selecionada.

O transporte alternativo é **JPEG por HTTPS a 720p, 1–15 FPS (padrão 10)**, exibido nas WebViews. É uma alternativa inicial implementada, não H.264 multistream nem equivalente em latência/eficiência a Moonlight. O portal informa o tipo da fonte e metadados; IDs de streams podem mudar quando o serviço reinicia, exigindo nova seleção. A conexão de vídeo por página tenta novamente após erro de rede, enquanto a autorização do portal continuar válida.

Não há injeção remota de mouse/teclado no serviço. Use mouse e teclado do PC. Para entrada remota real, falta implementar RemoteDesktop/libei com consentimento explícito; gestos sobre o JPEG não controlam o Linux. Gestos de conteúdo continuam válidos nos navegadores adicionais e na tela Moonlight principal.

## VA-API e hardware

No host, vainfo identificou i965 e H.264 EncSlice. Uma execução FFmpeg codificou 30 frames 720p em h264_vaapi com código 0. O driver iHD tentou inicializar e falhou antes do fallback para i965. Isso não demonstra ganho sustentado ou qualidade de três streams.

O Flatpak Sunshine tem `/dev/dri/renderD128`, mas a busca no seu `/usr/lib` encontrou iHD e não encontrou i965. Não foi alterada sua instalação nem sua configuração funcional. Não force VA-API no Flatpak sem disponibilizar um driver compatível com Ivy Bridge e testar a conexão. Por enquanto, o Sunshine existente pode continuar em libx264. O serviço JPEG não usa esse encoder H.264.

## Limitações e pendências

- A compilação e os testes automatizados estão descritos no relatório; os caminhos Android ainda precisam de teste em celular. Não foram feitos testes visuais de interface.
- Congelamento: foi removida a composição intermediária suspeita, mas a causa no aparelho não foi reproduzida; não se afirma correção comprovada. É necessário validar MediaCodec/OES, reconexão e EGL perdido com ARCore/MediaPipe ativos.
- Fallback Camera2 não alimenta MediaPipe; as mãos continuam dependentes do pipeline ARCore nesta versão. FOV/crop e orientação do fallback precisam de calibração por aparelho. A câmera é monocular duplicada para os olhos, sem profundidade estéreo nativa nem equivalência a transparência óptica.
- O posicionamento usa o referencial local ARCore, sem âncoras persistentes entre sessões. Um layout salvo restaura coordenadas relativas; não reconhece fisicamente o mesmo lugar do quarto. Com 3DoF, translação da cabeça não é rastreada. Não usar para locomoção confiando na imagem.
- Monitor principal preserva controles originais; renomear/remover/bloquear pelo novo gerenciador aplica-se às telas adicionais. A geometria usa proporção fixa 16:9 e tamanho uniforme, sem ajuste independente de altura. Rotação pitch/roll permanece no controle original e modelo, sem sliders adicionais.
- Seleção por olhar/permanência original preservada na tela principal. Seleção por olhar com permanência em todas as telas adicionais, calibração de raios fora do plano frontal e feedback de oclusão precisam de trabalho adicional. Duas mãos e bloqueio precisam de testes físicos.
- Perfis de qualidade não adaptam dinamicamente o bitrate do Moonlight. Suspensão fora de vista controla apenas a busca de frames das páginas do serviço; não desliga pipelines Linux individuais nem WebViews arbitrárias.
- Sem autodescoberta mDNS do serviço Android; endereço e token são configurados manualmente. O portal fornece seleção de fontes; um seletor nativo completo e IDs persistentes entre sessões são pendências.
- Criação real de múltiplos monitores virtuais, captura simultânea, uso de RAM, temperatura, bateria, latência e FPS em uma/duas/três telas precisam de teste integrado.
- Software antigo, drivers específicos e modelos de celular podem exigir ajustes. Os critérios de aceitação completos não foram atingidos nesta sessão.

## Licenças

README upstream declara GPL-3.0; o arquivo LICENSE upstream contém **AGPL-3.0** e o último commit tem esse título. Ambos foram preservados sem reclassificar a licença. Avisos/licenças Moonlight, Opus, OpenSSL e demais terceiros permanecem no código. Os novos arquivos não alteram as obrigações da distribuição original. A divergência deve ser esclarecida pelo titular antes de distribuir publicamente binários.
