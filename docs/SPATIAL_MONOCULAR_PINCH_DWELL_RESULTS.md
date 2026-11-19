# Spatial Monocular, Pinch & Dwell — validação v0.1.5

Base solicitada e conferida após fetch: `dae632e495304ca1ed102646d6b9004f379ff332`, na branch `fix/spatial-monocular-pinch-dwell`. A árvore estava limpa antes da troca de branch.

## Regressões reproduzidas e correções

A primeira rodada, com testes adicionais de regressão, executou 72 casos e teve quatro falhas reais: contadores esperavam uma confirmação e recebiam duas. Dois casos já pertenciam à branch (dois indicadores no mesmo conteúdo e oclusão por superfície indisponível); dois novos cobriam permanência seguida de pinça e uma perda de rastreamento de 50 ms depois de confirmar.

Mudanças de produção limitadas a `SpatialGesture.kt` e `FingertipInteraction.kt`:

- Manter a reserva de hover/tecla durante apontamento, impedindo confirmação simultânea do mesmo alvo pela outra mão.
- Tratar pinça e permanência como confirmações exclusivas da mesma sessão de apontamento, em ambas as ordens. Um alvo já confirmado não confirma de novo até mudar a intenção (mover, deixar o alvo ou deixar de apontar).
- Perda breve reinicia o tempo de permanência pendente, preservando o bloqueio de um alvo já confirmado. Nenhum clique é gerado durante ausência de observação. Perda longa continua encerrando a captura.

Testes adicionais incluem as duas ordens de confirmação, bloqueio após perda breve, rearme por movimento e uma sequência Android completa passando por `FingertipInteraction`. Os testes existentes de concorrência e oclusão foram preservados, sem relaxar expectativas.

## Spatial Monocular e piscadas

A branch remove os dois descartes do `HandTracker` que eliminavam a observação inteira quando a estimativa espacial ou a amostra espacial eram rejeitadas. Quando os landmarks passam nas validações existentes, a ausência da estimativa usa a projeção monocular anterior; ausência de metadados espaciais deixa os gestos de profundidade sem autorização, mas não apaga por si só a mão. `VrRenderer` continua usando validade/idade dos landmarks para desenho e o filtro visual de recuperação de 220 ms. Essa revisão confirma a remoção daquela causa específica de piscar no código.

Não foi executada inferência de câmera real nem replay de vídeo rotulado nesta entrega. Não é possível afirmar que a mão deixou de piscar no dispositivo: o detector pode perder a mão e os critérios de geometria/idade ainda podem recusar observações. O fallback monocular também pode apresentar mudanças de escala/profundidade; sua frequência e aparência continuam sem medição física.

## Testes e build executados

```sh
bash tools/workspace/build-android.sh :app:testDebugUnitTest :app:assembleDebug --offline --console=plain
python -m unittest discover -s tools/workspace -p 'test*.py'
python tools/workspace/verify-hand-model.py
```

| Suíte Android/JVM | Casos | Falhas |
|---|---:|---:|
| AndroidInteractionTest | 14 | 0 |
| CameraZoomTest | 3 | 0 |
| FingertipGestureTest | 11 | 0 |
| HandRenderRecoveryTest | 2 | 0 |
| InteractionPolicyTest | 12 | 0 |
| SpatialDualGestureTest | 9 | 0 |
| SpatialGestureTest | 6 | 0 |
| SpatialHandEngineTest | 7 | 0 |
| SpatialRaycastTest | 4 | 0 |
| WorkspaceGeometryTest | 5 | 0 |

**73 testes Android/JVM aprovados**, sem erros ou testes ignorados.

Linux: **8 testes da bridge aprovados**; driver nativo x86_64 e `tools/test_host.cpp` compilados, com **23 verificações aprovadas** no host com SteamVR simulado e painel web (`STORM_TEST_WEB=1`), sem falhas. O código Linux foi copiado para uma pasta temporária antes de rodar `build_linux.sh x86`, preservando o `dist` do checkout. O host usou configuração temporária via `STORMVR_DIR`.

Build final **BUILD SUCCESSFUL**; assinatura debug verificada com apksigner; pacote `com.stormvr.workspace.mr` confirmado com aapt; integridade do modelo de mãos aprovada. Gradle 8.7/JDK 17/NDK 27.0.12077973 existentes no ambiente. O núcleo Moonlight foi compilado para ARM64 e ARMv7. Avisos de APIs obsoletas/variáveis não usadas no Android e avisos do compilador C++ no driver Linux continuam presentes; não são reportados como erros de build ou falhas de teste.

## Artefato

Nome publicado: `StormVR-Workspace-MR-Spatial-Monocular-Pinch-Dwell-Debug.apk`.
Tamanho: 100265571 bytes.
SHA-256: `5c2ceafa6c8e4211866b63cc418f7620fce539d4c95fbb3f45cdf4aeebc29623`.
Manifest: `package: name='com.stormvr.workspace.mr' versionCode='1' versionName='0.1.0-alpha' platformBuildVersionName='14' platformBuildVersionCode='34' compileSdkVersion='34' compileSdkVersionCodename='14'`.

O APK permanece fora do histórico Git. A nova tag experimental aponta para o commit de correção descendente do solicitado; o hash exato é registrado na release. Nenhum merge na main foi realizado.

## Limitações restantes

- Nenhum dispositivo ADB conectado: piscar, precisão de pinça, falsos positivos, latência, iluminação, oclusões reais e consumo energético não foram medidos fisicamente.
- Os testes de gestos usam observações sintéticas; não provam a precisão do MediaPipe em vídeo. Movimento além da tolerância ou término da sessão de apontamento rearma uma nova intenção.
- Os testes Linux usam x86_64 e SteamVR simulado, perfil padrão; não validam SteamVR real, DroidDeck ARM, hardware Bluetooth ou Moonlight ao vivo.
- Depth/Raw Depth seguem experimentais, dependentes do aparelho e sujeitos a rejeição. Os demais limites documentados do rastreamento monocular permanecem.
