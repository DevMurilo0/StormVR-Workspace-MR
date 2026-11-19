# StormVR Spatial Hand Engine

Implementação incremental na branch `improve/vr-hands-input`. Os modos experimentais não são promovidos a padrão sem validação física. Nenhum segundo pipeline de câmera/modelo foi adicionado. Moonlight, teclado, bridge Linux e controles Bluetooth permanecem nos caminhos existentes.

## Usar e comparar

Em Mãos ou nas preferências do workspace:

- **Padrão atual**: projeção e prior anatômico anteriores, com correções compartilhadas de coordenadas, raios e previsão visual.
- **Spatial · monocular**: geometria robusta da palma, escala medida e amostras com metadados de confiança.
- **Spatial · Raw Depth experimental**: mesmo estimador, com fusão condicionada; amostras recusadas retornam ao estimador monocular. Dispositivo incompatível continua operando sem depth.

O seletor de estimativa é independente do modo de intenção: Indicador, Pinça clássica, Permanência ou Espacial experimental. Isso permite comparar apenas uma mudança por vez. Alterar estimativa, calibração ou modo encerra capturas. Para comparar literalmente com o código anterior, compilar o commit pai: as correções compartilhadas também se aplicam ao modo padrão desta versão.

Calibre a largura entre MCP do indicador e MCP do mínimo com régua, mão aberta (45–115 mm). O padrão de 80 mm é uma hipótese inicial, não uma medição do usuário. `handScale` continua como multiplicador existente; use 1,0 ao informar uma medida física. A medida é compartilhada entre as mãos nesta versão. O modelo monocular não fornece escala física individual observável sem referência externa.

No modo Espacial: aponte primeiro; pinça curta no conteúdo confirma na abertura (80–500 ms, sem excursão grande). Feche sobre barra/canto por 80 ms para capturar; mova mantendo o gesto; abra durante 80 ms para soltar. Pinça clássica continua com seus controles anteriores. A captura na barra move; a captura no canto redimensiona, sem clicar simultaneamente. Puxar/empurrar altera distância somente durante captura de movimento e com evidência consecutiva Raw Depth confiável; não depende da profundidade absoluta para apontar/clicar. Sem essa evidência, mover no plano continua disponível e a distância não acompanha a profundidade física incerta.

Ative **Diagnóstico espacial**: validade/observação por mão, confiança de consistência, distância, fonte de profundidade, fase, alvo, latência do pipeline e FPS. Ausência de confiança/distância no modo legado é exibida como `?`, não inventada. Retenção não significa nova observação. A latência exibida cobre fila/conversão/inferência desde envio ao tracker, não latência óptica até o display. O mesmo painel gera snapshots JSON de diagnóstico uma vez por segundo em `StormVRSpatial` no logcat, com CPU total do processo e PSS. O HUD permanece uma textura de 1400×120; reduz fonte para acomodar quatro linhas. Legibilidade em headset ainda exige inspeção física.

## Auditoria: problemas comprovados no código anterior

| Evidência | Consequência no algoritmo | Mudança |
|---|---|---|
| `HandTracker.process`: soma de segmentos projetados dividida pelo tamanho em pixels; profundidade limitada a 0,10–1,4 m | Profundidade não é medida; limites podem produzir posição aparentemente válida sem evidência física | Estimador separado usa mediana de pares estáveis da palma, intrínsecos por eixo, rejeita degeneração/discordância e distância fora de faixa |
| `wl[i].z()` somado à profundidade calculada da palma sem recentrar a origem do modelo | A origem relativa do modelo e o centro usado para escala podem divergir | Recentrar Z na média dos cinco pontos da palma; escalar também a geometria relativa |
| `VrRenderer.updateAr` guardava `displayOrientedPose` em `arM`; `submitHandFrame` marcava esse referencial como sensor e desfazia a rotação dos pixels | Pose de display tratada como pose física da câmera; possibilidade de rotação aplicada duas vezes | Captura usa `frame.camera.pose` com a mesma transformação para cena; pose de display continua usada no pipeline de cabeça |
| `predictionSeconds`: `now - sample + latency`, enquanto `sample` já era instante de envio | Tempo de inferência contado duas vezes na extrapolação visual | Usar idade da amostra uma única vez, mantendo previsão limitada e separada dos pontos de interação |
| `WorkspaceInteraction`: arraste clássico intersectava a geometria que acabava de alterar | Referência dependia da própria manipulação, especialmente em superfícies curvas | Reutilizar plano/superfície congelada e offset inicial de `FingertipManipulation` |
| `VrState.intersectLocal`: teste `dy < 0.05` | Resultado dependia da magnitude do vetor do raio | Teste relativo à norma, rejeição de raios nulos/não finitos |
| Comparação do monitor principal no workspace verificava apenas conteúdo | Barra/cantos do monitor principal podiam deixar passar seleção traseira | Raycast compartilhado inclui conteúdo e controles, com comparação por distância na cena |
| `VrRoomActivity`: depth sempre DISABLED e câmera forçava DO_NOT_USE | Não existia leitura de profundidade do ARCore | Detecção de suporte, configuração Raw Depth opcional, possibilidade de sensor extra ao iniciar a sessão experimental |

Essas consequências decorrem do código e dos testes sintéticos; sua frequência/percepção no headset anterior não foi medida nesta entrega.

## Hipóteses que dependem de dispositivo/dataset

- Oclusão, dedo dobrado e mudança de iluminação podem fazer o modelo errar articulações ou trocar identidades. Hand Landmarker não entrega aqui uma confiança de visibilidade observada por articulação; não se deve chamar uma articulação inferida de medição física.
- Foreshortening da palma e mudanças nos landmarks relativos do modelo podem causar drift de escala/profundidade. Pares descartados e mediana reduzem sensibilidade, sem resolver a ambiguidade monocular.
- Raw Depth pode medir fundo/parede sob uma mão em movimento, mesmo com confiança alta. Concordância espacial/monocular e baixa velocidade são heurísticas, não segmentação de mão nem garantia de superfície correta.
- Maior carga de depth pode piorar FPS, calor e energia; 30 fps pode ser necessário com sensor extra. O melhor delegate depende do aparelho. O auto-tuning GPU/CPU já existente continua ativo, devendo ser controlado durante comparações.

## Arquitetura e garantias de segurança de interação

`SpatialHandEngine` depende apenas de Kotlin e políticas puras. Recebe landmarks normalizados/relativos e intrínsecos, produz estimativa em câmera; o adaptador `HandTracker` transforma para cena usando pose de captura. Após associação de identidade, publica palma, cinco pontas, eixos unitários dos ossos, velocidade, timestamp e confiança. Eixo do osso não resolve torção articular completa. Nenhuma pose nova é produzida para dado inválido. O frame identifica retenção por `observed=false`; expira em 220 ms. A consistência geométrica é uma confiança heurística, não a probabilidade do detector nem o score de handedness.

A escala usa a largura 3D da palma do modelo normalizada à medida informada; profundidade vem de pares de palma com projeção suficiente, intrínsecos X/Y distintos e mediana, sem usar pontas móveis para escala. O prior que reposiciona ossos só continua no modo padrão. A filtragem temporal existente continua, mas não é a única mudança. Velocidade impossível, timestamp fora de ordem, geometria não finita e projeção insuficiente recusam a observação. Reinício/perda prolongada não extrapola pose física.

`SpatialGesture` reconhece intenção depois da visão: não há substituição de Hand Landmarker. Possui armamento com mão aberta apontando, debounce de captura/abertura, exclusividade de clique/mover/redimensionar, término explícito e rearme depois de abertura. Perda nunca confirma clique. Pequena perda congela manipulação; retomada recalcula o offset sem salto; perda longa encerra. Alvo de captura fica preso ao objeto; remoção, ocultação ou bloqueio cancela. Uma nova superfície na frente durante um toque pendente cancela o toque. A mesma janela não pode pertencer às duas mãos nem aos caminhos legado/espacial simultaneamente.

`SpatialRaycast` reutiliza interseções planas/curvas e transformações existentes. Ordena distância em cena; uma superfície bloqueada continua ocluindo. Clique em conteúdo não vira controle de redimensionamento. Controles externos maiores (mínimo 12 cm) preservam área do conteúdo. Os cursores existentes distinguem hover, armamento, captura, redimensionamento e confirmação; o HUD mostra liberação.

## ARCore Depth: critérios implementados

SDK do projeto: ARCore 1.44.0, MediaPipe tasks-vision 0.10.14, minSdk 26, compile/target 34. APIs Raw Depth já estão disponíveis nessa dependência. Não há declaração de depth obrigatório no manifest. `Session.isDepthModeSupported(RAW_DEPTH_ONLY)` decide em execução; rejeição de configuração desliga somente depth. Depth densa `AUTOMATIC` foi pesquisada, mas não usada na fusão: não oferece o mesmo mapa explícito de confiança e adiciona preenchimento/filtragem inadequados para presumir mão móvel.

1. Um único frame ARCore entrega imagem, intrínsecos e pose. Imagem CPU com timestamp distante mais de 5 ms do frame é descartada.
2. Copiar Raw Depth/confiança na thread GL e fechar ambas as imagens em `use`, inclusive em falhas. O worker não recebe objetos Image/Frame.
3. Rejeitar timestamps repetidos/reprojetados, diferenças entre mapas, mapas de dimensões diferentes e idade superior a 35 ms. Coordenadas da imagem original passam por transformação ARCore IMAGE_NORMALIZED → TEXTURE_NORMALIZED; rotação do bitmap é desfeita antes da consulta. Respeitar rowStride/pixelStride.
4. Exigir pelo menos quatro bases da palma com patches 3×3 de cinco pixels válidos, confiança ≥200/255, profundidade 120–1400 mm, dispersão local <45 mm e entre bases ≤70 mm.
5. Exigir concordância com monocular (máximo de 6 cm ou 18%). Fusão limitada a 75% Raw/25% monocular. Recusa retorna à estimativa monocular sem promover confiança física.
6. Manipulação em profundidade exige amostras consecutivas Raw, separadas por menos de 100 ms, velocidade da palma <0,65 m/s e mudança de distância <2,5 cm. A translação física também exige pose ARCore válida; fallback de giroscópio permite apontar, sem autorizar esse eixo. O deslocamento usa a palma em coordenadas de cena no eixo radial da captura, para não interpretar mudança de distância causada só pela cabeça como intenção de puxar. Amostra com idade acima de 80 ms no render, movimento mais rápido, ausência ou troca de fonte congela esse eixo e rearma o offset ao recuperar.

Esses critérios conservadores podem rejeitar quase toda a depth em certos aparelhos. Não são evidência de melhora no dispositivo. O botão Padrão continua disponível. Ao mudar para experimental numa sessão já aberta, a configuração atual de câmera é mantida; iniciar uma nova sessão experimental permite escolher configuração com sensor adicional. Imagens sem idade compatível com o relógio monotônico local são recusadas pelo estimador espacial; não se substitui esse instante por uma captura inventada. O modo padrão mantém sua política de tempo anterior. O relógio ARCore é usado diretamente nas comparações de depth e convertido para monotônico local apenas para validade de interação.

## Gesture Recognizer e classificadores personalizados

A API oferece landmarks junto com categorias estáticas e um classificador customizável. O bundle já contém detecção de palma/landmarks; executar Gesture Recognizer ao lado de Hand Landmarker duplicaria boa parte do trabalho. As categorias prontas (por exemplo punho e palma aberta) não descrevem intenção temporal de puxar, tocar ou manipular um objeto capturado.

O benchmark público do Google para Pixel 6 reporta média de **16,76 ms CPU / 20,87 ms GPU** para o pipeline Gesture Recognizer. São resultados publicados, **não medições StormVR**, nem custo incremental sobre este app. Não há benchmark de CPU/memória/energia ou precisão do classificador no aparelho do usuário nesta entrega. Sem dataset e dispositivo para demonstrar utilidade, não integramos um segundo reconhecedor nem substituímos o modelo atual.

Uma integração posterior deve comparar (A) Landmarker + geometria e (B) um único Gesture Recognizer fornecendo os mesmos landmarks + classificador opcional. Outra opção é treinar um classificador leve sobre landmarks já extraídos, com validação por pessoa e sessão, classes negativas/oclusão e rejeição de desconhecidos. Não usar divisão aleatória de frames adjacentes como evidência de generalização. Orientação/gestos estáticos podem auxiliar o motor temporal, sem autorizar clique diretamente por categoria.

## Protocolo físico e coleta reproduzível

No mesmo aparelho, mesma iluminação, resolução, delegate, teto de FPS e stream Moonlight, alternar padrão, spatial monocular e raw; alternar a ordem dos modos entre repetições para reduzir viés térmico. Realizar pelo menos três sessões por modo, incluindo repouso (60 s), rotações do pulso (60 s), apontar/tocar (30 tentativas), captura/mover/soltar (30), profundidade lenta/rápida (30), duas mãos cruzando, oclusão 100/300/1000 ms, janelas sobrepostas/ocultas/bloqueadas, teclado e gatilho Bluetooth durante captura. Incluir planos e curvas e cabeça em movimento. Registrar ações esperadas e observadas, cliques falsos, solturas falsas, identidade, jitter em repouso e erro de distância contra referência física.

Ativar diagnóstico e executar, sem caminhos presumidos:

```sh
adb devices -l
python tools/workspace/capture-spatial-benchmark.py --serial SERIAL --seconds 120 --output resultados/spatial-01
```

`--adb` aceita executável escolhido pelo usuário. O script coleta snapshots JSON, meminfo, propriedades e batterystats/battery antes/depois; não reseta estatísticas nem altera preferências. A pasta de saída deve ser nova. Snapshots a 1 Hz são amostragem de telemetria, não distribuição de todos os frames. Para percentis reais de latência ou consumo incremental, usar tracing Perfetto/profiler e medidor de energia, com tela/rede fixas e carregamento controlado. CPU é uso de todo o processo em equivalentes de um núcleo, podendo ultrapassar 100%; PSS inclui streaming. Batterystats pode ser incompleto e é cumulativo: diferença isolada não prova consumo do classificador. Coleta PSS/diagnóstico tem overhead; repetir testes com diagnóstico desligado.

Os testes automatizados cobrem álgebra, políticas e transições; não substituem as sessões físicas, a inspeção visual do painel, a identificação do sensor ou os benchmarks de reconhecimento. Resultados reais de build/teste desta entrega constam em `SPATIAL_HAND_RESULTS.md`.

## Fontes primárias consultadas

- [ARCore Depth](https://developers.google.com/ar/develop/depth): profundidade baseada em movimento/sensor, validade e limitações.
- [Raw Depth Android](https://developers.google.com/ar/develop/java/depth/raw-depth): confiança 0–255, pixels ausentes e timestamp repetido indicando reprojeção.
- [Config.DepthMode](https://developers.google.com/ar/reference/java/com/google/ar/core/Config.DepthMode): RAW_DEPTH_ONLY, AUTOMATIC e suporte em execução.
- [Frame](https://developers.google.com/ar/reference/java/com/google/ar/core/Frame): aquisição e transformação de coordenadas.
- [Gesture Recognizer](https://developers.google.com/edge/mediapipe/solutions/vision/gesture_recognizer): modelos, categorias, customização e benchmark público.
- [On-Device Real-Time Custom Hand Gesture Recognition, ICCV 2023](https://openaccess.thecvf.com/content/ICCV2023W/CV4Metaverse/papers/Uboweja_On-Device_Real-Time_Custom_Hand_Gesture_Recognition_ICCVW_2023_paper.pdf): treinamento de classificadores sobre embeddings de landmarks.
