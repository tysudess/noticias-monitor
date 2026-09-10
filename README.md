# Monitor de Notícias Android

Aplicativo Android em Kotlin/Jetpack Compose para monitoramento de notícias, termos, demandas e veículos de imprensa.

## Próxima versão
**2.5.0**

### Principais recursos
- Busca no Google Notícias via RSS
- Detecção de notícias realmente novas
- Demandas com pesquisa própria por veículo + assunto
- Busca manual de todas as demandas
- Busca manual de uma demanda individual
- Monitoramento automático das demandas aproximadamente a cada 1 hora via WorkManager
- Status persistente por demanda: última execução, encontrados, novos e erro
- Histórico local e exportação CSV
- Intervalo de monitoramento persistente
- Notificações para novas notícias e demandas
- Navegação superior com abas deslizáveis
- Seleção persistente de fontes
- Busca por veículo/grupo e filtros por região/estado
- Selecionar/desmarcar as fontes visíveis
- Modo `Buscar em todos os veículos` para busca aberta, inclusive veículos menores
- 13 veículos nacionais predefinidos
- Catálogo estadual inicial com 4 veículos de referência por UF
- Pesquisa por período com data e hora inicial/final persistentes
- Atalhos Hoje / 24 horas / 7 dias / 30 dias
- Validação de períodos inválidos

## Fontes nacionais incluídas
O Globo, Correio Braziliense, G1, R7, Revista Oeste, Folha de S.Paulo, Estadão, Valor Econômico, O Antagonista, CNN Brasil, Jovem Pan, Estado de Minas e Metrópoles.

> O catálogo estadual é uma curadoria inicial de veículos de grande relevância/alcance em cada UF. O modo de busca aberta continua disponível para encontrar veículos fora desse catálogo.

## Preservação de dados ao sair da v2.4

A Release v2.4.0 foi assinada com uma chave de debug efêmera do GitHub Actions. Como essa chave privada não existe mais, a primeira transição para a assinatura permanente exige uma migração única por ADB.

O utilitário `tools/migrate_v24_data.py`:
1. salva `news.db` e `shared_prefs` da v2.4 usando `run-as`;
2. valida o backup antes de remover qualquer app;
3. instala uma build de migração v2.5, assinada com a nova chave permanente;
4. restaura banco e preferências;
5. abre o app uma vez para executar a migração SQLite 2 → 3;
6. instala por cima o APK final v2.5 com a mesma chave permanente.

Depois dessa transição única, as versões futuras usam a mesma chave permanente e podem atualizar normalmente sem desinstalar o aplicativo.

## Assinatura permanente

O projeto nunca deve versionar `.jks`/`.keystore`. O workflow de Release receberá a chave por GitHub Actions Secrets e assinará tanto o APK de migração quanto o APK final com o mesmo certificado.

Secrets previstos:
- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`

Alias fixo: `monitor-noticias`.

## Build
O projeto usa Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Java 17, compileSdk 35 e targetSdk 35.

A automação em `.github/workflows/release.yml` compila o APK no GitHub Actions. Pull requests são usados para validação e o `main` publica a Release somente quando a assinatura permanente estiver configurada.
