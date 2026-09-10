# Monitor de Notícias — Windows Portable v4.0.2

Esta edição porta a **v4.0.2** para Windows sem substituir nem reduzir o aplicativo Android.

## Paridade funcional

O módulo `desktop` compila diretamente o mesmo motor Kotlin/JVM usado no Android para:

- busca progressiva de Notícias;
- busca progressiva de Vídeos;
- busca por período;
- Termos de Notícias independentes dos Termos de Vídeos;
- Demandas por veículo + assunto e varredura de todas as Demandas;
- catálogo completo de fontes de Notícias;
- catálogo completo de fontes de Vídeos;
- YouTube oficial;
- Globoplay por Edições, Trechos e Jarvis;
- telejornais nacionais e regionais;
- varredura por fonte/canal seguida de cruzamento local com Termos e Demandas;
- metadados, título, descrição e demais campos já usados pelo motor v4.0.2;
- histórico persistente;
- deduplicação;
- preservação da primeira captura e regra do selo **NOVO** baseada no histórico;
- diagnóstico de fontes de vídeo instáveis.

Somente as camadas específicas do Android são substituídas: `SQLiteOpenHelper` por SQLite JDBC, `Context/SharedPreferences` por armazenamento portátil, `WorkManager/AlarmManager` por agendamento residente no tray e a UI móvel por uma UI desktop.

## Monitoramento automático no Windows

- Notícias: intervalo configurável, mínimo de 15 minutos.
- Demandas: uma vez por hora.
- Vídeos: 08h, 12h, 15h, 19h e 21h no horário local, iguais à rotina Android.
- Fechar a janela envia o programa para a bandeja do sistema; **Sair** no menu da bandeja encerra o monitoramento.

## Dados portáteis

A distribuição cria uma pasta `data` junto ao aplicativo. Nela ficam:

- `news.db`
- `videos.db`
- preferências
- termos
- demandas
- histórico

Copiar a pasta completa do programa para outro computador preserva os dados da edição Windows.

## Compilar localmente no Windows

Requer JDK 17 e Gradle 8.10.2 (ou compatível):

```powershell
gradle :desktop:createDistributable
```

O app image fica em `desktop\build\compose\binaries\main\app\`.

## Artefato automático

O workflow `Windows Portable v4.0.2` roda em `windows-latest`, cria o app image com runtime Java incluído e publica o ZIP `monitor-de-noticias-windows-portable-v4.0.2.zip` como artefato do GitHub Actions.
