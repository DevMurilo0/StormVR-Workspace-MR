# Mãos e interação: diagnóstico e validação

## Causas comprovadas no código anterior

- `HandTracker`: um `lastSeenNs` compartilhado era atualizado por qualquer mão. Uma mão ausente podia permanecer válida indefinidamente enquanto a outra era detectada.
- `VrRenderer`: velocidade limitada a 3 m/s extrapolada por até 140 ms permitia deslocamento adicional de 42 cm por eixo, inclusive durante poses mantidas por oclusão.
- `HandTracker`: calibração do rótulo baseada na ordem horizontal das duas mãos mudava ao cruzar os braços. Associação local também podia preferir o mesmo slot para duas detecções.
- `WorkspaceInteraction`: ACTION_UP exigia interseção no quadro de soltura, convertendo um clique válido em CANCEL quando o raio escapava do plano. Duas mãos podiam criar fluxos de toque independentes com ACTION_DOWN no mesmo decorView.
- Escala usava TEX_W/TEX_H em vez do último pixel válido. Não havia confirmação de que o destino recebeu o evento.
- `VrRoomActivity`: teclado inseria em `web` ou Moonlight, sem selecionar o WebView da Presentation adicional. Acionava teclas ao apertar e também pelo pipeline físico de poke, duas fontes para o mesmo teclado.
- `HandTracker`: para pose física ARCore, os eixos da imagem girada eram usados sem desfazer a rotação da imagem, embora a pose física da câmera não gire com ela.

## Alterações

Validade e idade por mão; janela máxima de 220 ms; previsão só com amostra recente/observada e inferência ≤100 ms, limitada a 25 ms e ±1,5 cm/eixo; filtros existentes retunados; rejeição de NaN/Inf, saltos de punho e segmentos excessivos; recuperação com reset de velocidade, filtro e rearme aberto; associação global mínima para duas mãos e rótulo inicial sem recalibração pela posição horizontal. Oclusão breve mantém pose sem previsão, depois remove a mão.

Pontos filtrados de interação separados da suavização visual. Estilos completo/simplificado/ponteiros persistentes. Modelo oficial empacotado sem alterações com licença Apache 2.0 e SHA-256; fonte e licença em assets/HAND_MODEL_NOTICE.txt. Referência primária: https://developers.google.com/edge/mediapipe/solutions/vision/hand_landmarker e model card indicado no notice.

Captura explícita PRESSED/DRAGGING/IDLE, alvo fixo, tolerância espacial, soltura nas últimas coordenadas aceitas, cancelamento em perda/pausa/remoção, um proprietário por display e preferência pela mão dominante. Escala com duas mãos somente em manipulação da moldura, não durante conteúdo. Teclado/barra precedem telas adicionais; conteúdo principal participa da comparação de profundidade. Coordenadas ajustadas ao decorView real, UI thread exigida, cópias de MotionEvent recicladas. Feedback de clique indica envio aceito pelo Android, não sucesso de ações externas.

Teclado por pinça/soltura, permanência ou gamepad, modos separados e Unicode via InputConnection; teclado desenhado em textura não toma foco. Poke removido do laço ativo para evitar digitação duplicada. Repetição restrita ao Backspace. Preferências possuem namespace próprio e restauração sem apagar layouts/Moonlight.

## Testes e build

Execução desta alteração, 18/11/2026:

| Verificação | Resultado real |
| --- | --- |
| `:app:testDebugUnitTest :app:assembleDebug` | **BUILD SUCCESSFUL**, 8 min 13 s |
| WorkspaceGeometryTest existente | 5 testes, 0 falhas |
| InteractionPolicyTest | 12 testes, 0 falhas |
| AndroidInteractionTest (Robolectric, API 28) | 4 testes, 0 falhas |
| Total JUnit | **21 testes, 0 falhas, 0 erros, 0 ignorados** |
| Serviço Linux `unittest discover` | **8 testes aprovados**, 4,589 s |
| `apksigner verify --verbose` | Assinatura v2 válida, 1 signatário |
| `aapt dump badging` | `com.stormvr.workspace.mr`, minSdk 26, targetSdk 34 |
| ABIs no APK | `arm64-v8a`, `armeabi-v7a` |
| Modelo extraído do APK | Tamanho e SHA-256 corretos |
| `git diff --check` | Sem erros de whitespace |
| Aparelhos por `adb devices` | Nenhum conectado; instalação e execução física não testadas |

APK: `Source/android/app/build/outputs/apk/debug/app-debug.apk` (100,222,947 bytes).
Cópia identificada: `dist/handtracking-debug/StormVR-Workspace-MR-HandTracking-Debug.apk`.
SHA-256: `975128b71f5e399a1e019946dcbdcc4004ff456ae7573fa07f6e87c7411559d7`.
Logs, XMLs e relatório HTML em `dist/handtracking-debug/`. Não confundir com `TEST_RESULTS.md`, que descreve o build alpha anterior.

Os testes de Android verificam editor nativo focado (Unicode/Backspace), persistência e restauração de preferências, geometria dos modos do teclado e sequências Android derivadas da captura. Não executam páginas externas nem uma Presentation em aparelho. O workflow está criado localmente, sem push, execução no GitHub ou publicação de release nesta sessão.

## Limites

Sem aparelho conectado nesta sessão. Não foram validados fisicamente detecção MediaPipe, alinhamento/escala, visual estéreo, recuperação de câmera, latência, temperatura, rolagem por gestos ou integração Moonlight. A inferência monocular continua tendo ambiguidade de profundidade; cruzamento/oclusão total pode perder identidade. A evidência automatizada cobre políticas e Android simulado, não garante precisão física. Não foi acrescentado protocolo remoto à ponte Linux JPEG/HTTPS.

## Arquivos de implementação

- `HandTracker.kt`: amostras por mão, pinça, associação, modelo e coordenadas da câmera.
- `VrRenderer.kt`: previsão limitada, suavização visual, estilos, indicadores e permanência.
- `VrState.kt`: pontos de interação e preferências compartilhadas.
- `VrRoomActivity.kt`: captura das teclas, arbitragem, texto, foco, permanência e pausa.
- `VrUi.kt`: teclado em modos, letras grandes, destaque e progresso.
- `WorkspaceInteraction.kt`: captura, eventos, profundidade e manipulação exclusiva.
- `WorkspaceDisplays.kt`: envio para Presentation, coordenadas reais e destino de texto.
- `WorkspacePanel.kt`: controles persistentes, restauração e confirmação de remoção.
- `InteractionPolicy.kt`, `InteractionSettings.kt`, `VrTextInput.kt`: políticas testáveis, persistência e editor focado.
- `InteractionPolicyTest.kt`, `AndroidInteractionTest.kt`: testes novos; `WorkspaceGeometryTest.kt` preservado.
- `app/build.gradle.kts`: Robolectric só na configuração de testes.
- `assets/hand_landmarker.task`, `HAND_MODEL_NOTICE.txt`, `MEDIAPIPE-LICENSE.txt`: modelo offline e atribuição.
- `tools/workspace/verify-hand-model.py`, `.github/workflows/handtracking-debug.yml`, `docs/BUILD_APK.md`: integridade, build manual no GitHub e instalação.
