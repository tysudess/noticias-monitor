package br.com.monitordenoticias.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import br.com.monitordenoticias.android.*
import kotlinx.coroutines.delay
import java.awt.Desktop as AwtDesktop
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Section(val label:String,val icon:ImageVector) {
    HOME("Início",Icons.Default.Home),
    NEWS("Notícias",Icons.Default.Article),
    VIDEOS("Vídeos",Icons.Default.PlayCircle),
    TERMS("Termos",Icons.Default.Search),
    DEMANDS("Demandas",Icons.Default.NotificationsActive),
    SOURCES("Fontes",Icons.Default.Public),
    HISTORY("Histórico",Icons.Default.History),
    SETTINGS("Configurações",Icons.Default.Settings)
}

fun main() = application {
    val trayState=rememberTrayState()
    var windowVisible by remember { mutableStateOf(true) }
    val controller=remember {
        DesktopController { title,message ->
            runCatching { trayState.sendNotification(Notification(title,message)) }
        }
    }
    val trayIcon=rememberVectorPainter(Icons.Default.Newspaper)

    Tray(
        state=trayState,
        icon=trayIcon,
        tooltip="Monitor de Notícias v4.0.2",
        menu={
            Item("Abrir Monitor de Notícias", onClick={windowVisible=true})
            Separator()
            Item("Buscar notícias agora", onClick={controller.searchNews()})
            Item("Buscar vídeos agora", onClick={controller.searchVideos()})
            Item("Buscar demandas agora", onClick={controller.searchAllDemands()})
            Separator()
            Item("Sair", onClick={controller.close(); exitApplication()})
        }
    )

    Window(
        visible=windowVisible,
        onCloseRequest={windowVisible=false},
        title="Monitor de Notícias • Windows Portable v4.0.2",
        state=rememberWindowState(width=1380.dp,height=860.dp)
    ) {
        MaterialTheme(colorScheme=if(isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
            App(controller)
        }
    }
}

@Composable
private fun App(c:DesktopController) {
    var section by remember { mutableStateOf(Section.HOME) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while(true){ delay(500); tick++ } }
    @Suppress("UNUSED_VARIABLE") val redraw=tick

    Row(Modifier.fillMaxSize()) {
        NavigationRail(
            header={
                Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.padding(vertical=12.dp)) {
                    Icon(Icons.Default.Newspaper,null,modifier=Modifier.size(34.dp))
                    Text("Monitor",style=MaterialTheme.typography.labelLarge)
                    Text("v4.0.2",style=MaterialTheme.typography.labelSmall)
                }
            }
        ) {
            Section.entries.forEach {
                NavigationRailItem(
                    selected=section==it,
                    onClick={section=it},
                    icon={Icon(it.icon,null)},
                    label={Text(it.label)}
                )
            }
        }
        VerticalDivider()
        Column(Modifier.weight(1f).fillMaxHeight()) {
            DesktopTopBar(c,section)
            HorizontalDivider()
            Box(Modifier.fillMaxSize().padding(18.dp)) {
                when(section) {
                    Section.HOME -> HomeScreen(c)
                    Section.NEWS -> NewsScreen(c)
                    Section.VIDEOS -> VideosScreen(c)
                    Section.TERMS -> TermsScreen(c)
                    Section.DEMANDS -> DemandsScreen(c)
                    Section.SOURCES -> SourcesScreen(c)
                    Section.HISTORY -> HistoryScreen(c)
                    Section.SETTINGS -> SettingsScreen(c)
                }
            }
        }
    }
}

@Composable
private fun DesktopTopBar(c:DesktopController,section:Section) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal=20.dp),
        verticalAlignment=Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(section.label,style=MaterialTheme.typography.titleLarge)
            Text("Monitor de Notícias • Windows Portable",style=MaterialTheme.typography.labelMedium)
        }
        AssistChip(onClick={},label={Text(if(c.automaticMonitoring)"Monitoramento automático ativo" else "Monitoramento automático pausado")},
            leadingIcon={Icon(if(c.automaticMonitoring) Icons.Default.CheckCircle else Icons.Default.Pause,null)})
    }
}

@Composable
private fun HomeScreen(c:DesktopController) {
    LazyColumn(verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.fillMaxWidth()) {
                MetricCard("Notícias • 24h",c.news.size.toString(),Icons.Default.Article,Modifier.weight(1f))
                MetricCard("Vídeos • 7 dias",c.videos.size.toString(),Icons.Default.PlayCircle,Modifier.weight(1f))
                MetricCard("Demandas",c.demands.count{it.active}.toString(),Icons.Default.NotificationsActive,Modifier.weight(1f))
                MetricCard("Fontes", "${SourceCatalog.all.size} + ${VideoSourceCatalog.all.size}",Icons.Default.Public,Modifier.weight(1f))
            }
        }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("Ações rápidas",style=MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Button(onClick={c.searchNews()},enabled=!c.newsBusy){Icon(Icons.Default.Refresh,null);Spacer(Modifier.width(6.dp));Text("Buscar notícias")}
                        Button(onClick={c.searchVideos()},enabled=!c.videoBusy){Icon(Icons.Default.Refresh,null);Spacer(Modifier.width(6.dp));Text("Buscar vídeos")}
                        OutlinedButton(onClick={c.searchAllDemands()},enabled=!c.newsBusy){Text("Buscar demandas")}
                    }
                    Text(c.status)
                    Text(c.videoStatus)
                }
            }
        }
        item { ProgressPanel("Notícias",c.newsProgress) }
        item { ProgressPanel("Vídeos",c.videoProgress) }
        if(c.unstableVideoSources.isNotEmpty()) item {
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Fontes de vídeo instáveis",style=MaterialTheme.typography.titleMedium)
                    c.unstableVideoSources.forEach { Text("• ${it.sourceName} — ${it.stage} (${it.failureCount})") }
                }
            }
        }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text("Agendamento automático",style=MaterialTheme.typography.titleMedium)
                    Text("Notícias: a cada ${c.newsIntervalMinutes} minutos • Demandas: a cada 1 hora")
                    Text("Vídeos: 08h, 12h, 15h, 19h e 21h, no horário local do Windows")
                    Text("Fechar a janela mantém o aplicativo na bandeja do sistema para preservar o monitoramento.")
                }
            }
        }
    }
}

@Composable
private fun MetricCard(title:String,value:String,icon:ImageVector,modifier:Modifier=Modifier) {
    Card(modifier) {
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(icon,null,modifier=Modifier.size(30.dp))
            Spacer(Modifier.width(12.dp))
            Column { Text(value,style=MaterialTheme.typography.headlineSmall);Text(title,style=MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
private fun ProgressPanel(title:String,p:LiveSearchProgress) {
    if(!p.active && p.startedAt==0L) return
    Card {
        Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row { Text("$title • ${if(p.active)"buscando" else "concluído"}",style=MaterialTheme.typography.titleSmall);Spacer(Modifier.weight(1f));Text("${p.completed}/${p.total}") }
            LinearProgressIndicator(progress={p.fraction},modifier=Modifier.fillMaxWidth())
            Text("${p.currentSource}${if(p.currentQuery.isNotBlank())" • ${p.currentQuery}" else ""}")
            val end=if(p.active)System.currentTimeMillis() else p.finishedAt
            val sec=((end-p.startedAt).coerceAtLeast(0L)/1000)
            Text("Tempo ${sec/60}:${(sec%60).toString().padStart(2,'0')} • encontrados ${p.found} • novos ${p.newCount} • falhas ${p.errors}",style=MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun NewsScreen(c:DesktopController) {
    var onlyDemands by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var sd by remember { mutableStateOf("") }; var st by remember { mutableStateOf("00:00") }
    var ed by remember { mutableStateOf("") }; var et by remember { mutableStateOf("23:59") }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick={c.searchNews()},enabled=!c.newsBusy){Text("Buscar últimas 24h")}
            FilterChip(selected=onlyDemands,onClick={onlyDemands=!onlyDemands},label={Text("Só Demandas")})
            OutlinedTextField(query,{query=it},label={Text("Filtrar")},singleLine=true,modifier=Modifier.width(260.dp))
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
            OutlinedTextField(sd,{sd=it},label={Text("Data inicial (AAAA-MM-DD)")},singleLine=true,modifier=Modifier.width(190.dp))
            OutlinedTextField(st,{st=it},label={Text("Hora")},singleLine=true,modifier=Modifier.width(100.dp))
            OutlinedTextField(ed,{ed=it},label={Text("Data final")},singleLine=true,modifier=Modifier.width(190.dp))
            OutlinedTextField(et,{et=it},label={Text("Hora")},singleLine=true,modifier=Modifier.width(100.dp))
            OutlinedButton(onClick={c.parsePeriod(sd,st,ed,et)?.let{c.searchNews(it.first,it.second)}},enabled=!c.newsBusy){Text("Buscar período")}
        }
        ProgressPanel("Notícias",c.newsProgress)
        Text(c.status,style=MaterialTheme.typography.labelLarge)
        val items=c.news.filter { (!onlyDemands || it.demand) && (query.isBlank() || "${it.title} ${it.source} ${it.matchedTerm} ${it.matchedDemand}".contains(query,true)) }
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.weight(1f)) {
            items(items,key={it.link}) { NewsCard(it) }
        }
    }
}

@Composable
private fun NewsCard(n:News) {
    Card(Modifier.fillMaxWidth().clickable{openUrl(n.link)}) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Row {
                Text(n.title,style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                if(System.currentTimeMillis()-n.capturedAt < 24*60*60*1000L) SuggestionChip(onClick={},label={Text("NOVO")})
            }
            Text("${n.source} • ${formatDate(n.date)}",style=MaterialTheme.typography.labelMedium)
            if(n.snippet.isNotBlank()) Text(n.snippet,maxLines=2)
            if(n.matchedTerm.isNotBlank()) Text("Termo: ${n.matchedTerm}",style=MaterialTheme.typography.labelSmall)
            if(n.matchedDemand.isNotBlank()) Text("Demanda: ${n.matchedDemand}",style=MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun VideosScreen(c:DesktopController) {
    var filter by remember { mutableStateOf(VideoFilter.ALL) }
    var query by remember { mutableStateOf("") }
    var sd by remember { mutableStateOf("") }; var st by remember { mutableStateOf("00:00") }
    var ed by remember { mutableStateOf("") }; var et by remember { mutableStateOf("23:59") }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
            Button(onClick={c.searchVideos()},enabled=!c.videoBusy){Text("Buscar vídeos")}
            VideoFilter.entries.forEach { f -> FilterChip(selected=filter==f,onClick={filter=f},label={Text(when(f){VideoFilter.ALL->"Todos";VideoFilter.RELEVANT->"Relevantes";VideoFilter.DEMANDS->"Demandas"})}) }
            OutlinedTextField(query,{query=it},label={Text("Filtrar")},singleLine=true,modifier=Modifier.width(240.dp))
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
            OutlinedTextField(sd,{sd=it},label={Text("Data inicial")},singleLine=true,modifier=Modifier.width(180.dp))
            OutlinedTextField(st,{st=it},label={Text("Hora")},singleLine=true,modifier=Modifier.width(100.dp))
            OutlinedTextField(ed,{ed=it},label={Text("Data final")},singleLine=true,modifier=Modifier.width(180.dp))
            OutlinedTextField(et,{et=it},label={Text("Hora")},singleLine=true,modifier=Modifier.width(100.dp))
            OutlinedButton(onClick={c.parsePeriod(sd,st,ed,et)?.let{c.searchVideos(it.first,it.second)}},enabled=!c.videoBusy){Text("Buscar período")}
        }
        ProgressPanel("Vídeos",c.videoProgress)
        Text(c.videoStatus,style=MaterialTheme.typography.labelLarge)
        val items=c.videos.filter {
            (filter==VideoFilter.ALL || filter==VideoFilter.RELEVANT&&it.relevant || filter==VideoFilter.DEMANDS&&it.demand) &&
                (query.isBlank() || "${it.title} ${it.sourceName} ${it.summary} ${it.matchedTerm} ${it.matchedDemand}".contains(query,true))
        }
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.weight(1f)) {
            items(items,key={it.link}) { VideoCard(it) }
        }
    }
}

@Composable
private fun VideoCard(v:VideoItem) {
    Card(Modifier.fillMaxWidth().clickable{openUrl(v.link)}) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Row {
                Icon(Icons.Default.PlayCircle,null);Spacer(Modifier.width(8.dp))
                Text(v.title,style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                if(System.currentTimeMillis()-v.capturedAt < 24*60*60*1000L) SuggestionChip(onClick={},label={Text("NOVO")})
            }
            Text("${v.sourceName} • ${formatDate(v.publishedAt)}",style=MaterialTheme.typography.labelMedium)
            if(v.summary.isNotBlank()) Text(v.summary,maxLines=2)
            if(v.matchedTerm.isNotBlank()) Text("Termo: ${v.matchedTerm}",style=MaterialTheme.typography.labelSmall)
            if(v.matchedDemand.isNotBlank()) Text("Demanda: ${v.matchedDemand}",style=MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun TermsScreen(c:DesktopController) {
    var newsTerm by remember { mutableStateOf("") }
    var videoTerm by remember { mutableStateOf("") }
    Row(horizontalArrangement=Arrangement.spacedBy(16.dp),modifier=Modifier.fillMaxSize()) {
        TermColumn("Termos de Notícias",c.terms,newsTerm,{newsTerm=it},{c.addTerm(newsTerm);newsTerm=""},c::removeTerm,Modifier.weight(1f))
        TermColumn("Termos de Vídeos",c.videoTerms,videoTerm,{videoTerm=it},{c.addVideoTerm(videoTerm);videoTerm=""},c::removeVideoTerm,Modifier.weight(1f))
    }
}

@Composable
private fun TermColumn(title:String,items:List<String>,value:String,onValue:(String)->Unit,onAdd:()->Unit,onRemove:(String)->Unit,modifier:Modifier) {
    Card(modifier.fillMaxHeight()) {
        Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(title,style=MaterialTheme.typography.titleMedium)
            Row(verticalAlignment=Alignment.CenterVertically) {
                OutlinedTextField(value,onValue,label={Text("Novo termo")},singleLine=true,modifier=Modifier.weight(1f))
                Spacer(Modifier.width(8.dp));Button(onClick=onAdd,enabled=value.isNotBlank()){Text("Adicionar")}
            }
            LazyColumn(Modifier.weight(1f)) {
                items(items) { term ->
                    Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text(term,modifier=Modifier.weight(1f));IconButton(onClick={onRemove(term)}){Icon(Icons.Default.Delete,null)}
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun DemandsScreen(c:DesktopController) {
    var vehicle by remember { mutableStateOf("") };var subject by remember { mutableStateOf("") }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
            OutlinedTextField(vehicle,{vehicle=it},label={Text("Veículo")},singleLine=true,modifier=Modifier.width(260.dp))
            OutlinedTextField(subject,{subject=it},label={Text("Assunto")},singleLine=true,modifier=Modifier.weight(1f))
            Button(onClick={c.addDemand(vehicle,subject);vehicle="";subject=""},enabled=vehicle.isNotBlank()&&subject.isNotBlank()){Text("Adicionar")}
            OutlinedButton(onClick={c.searchAllDemands()},enabled=!c.newsBusy){Text("Buscar todas")}
        }
        Text(c.status)
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.weight(1f)) {
            items(c.demands,key={it.id}) { d ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${d.vehicle} • ${d.subject}",style=MaterialTheme.typography.titleMedium)
                            Text("Última busca: ${if(d.lastCheckedAt>0)formatDate(d.lastCheckedAt) else "nunca"} • encontrados ${d.lastFoundCount} • novos ${d.lastNewCount}",style=MaterialTheme.typography.labelMedium)
                            if(d.lastError.isNotBlank()) Text(d.lastError)
                        }
                        OutlinedButton(onClick={c.searchDemand(d)},enabled=!c.newsBusy){Text("Buscar")}
                        IconButton(onClick={c.removeDemand(d.id)}){Icon(Icons.Default.Delete,null)}
                    }
                }
            }
        }
    }
}

@Composable
private fun SourcesScreen(c:DesktopController) {
    var videos by remember { mutableStateOf(false) };var query by remember { mutableStateOf("") }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
            FilterChip(selected=!videos,onClick={videos=false},label={Text("Notícias")})
            FilterChip(selected=videos,onClick={videos=true},label={Text("Vídeos")})
            OutlinedTextField(query,{query=it},label={Text("Filtrar fonte")},singleLine=true,modifier=Modifier.width(300.dp))
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick={if(videos)c.selectAllVideoSources() else c.selectAllNewsSources()}){Text("Selecionar todas")}
            OutlinedButton(onClick={if(videos)c.clearVideoSources() else c.clearNewsSources()}){Text("Limpar")}
        }
        if(!videos && c.newsAllSources) Text("Modo Notícias: todas as fontes do catálogo estão ativas.",style=MaterialTheme.typography.labelLarge)
        val list = if(videos) {
            VideoSourceCatalog.all.filter{query.isBlank() || "${it.name} ${it.group} ${it.state}".contains(query,true)}
                .map { Triple(it.id,it.name,"${it.group}${if(it.state.isNotBlank())" • ${it.state}" else ""}") }
        } else {
            SourceCatalog.all.filter{query.isBlank() || "${it.name} ${it.group} ${it.stateName}".contains(query,true)}
                .map { Triple(it.id,it.name,"${it.group} • ${it.state}") }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(list,key={it.first}) { s ->
                val checked=if(videos) s.first in c.selectedVideoSourceIds else c.newsAllSources || s.first in c.selectedNewsSourceIds
                Row(Modifier.fillMaxWidth().clickable{
                    if(videos)c.setVideoSource(s.first,!checked) else c.setNewsSource(s.first,!checked)
                }.padding(vertical=6.dp,horizontal=4.dp),verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(checked,onCheckedChange={v->if(videos)c.setVideoSource(s.first,v) else c.setNewsSource(s.first,v)})
                    Column {Text(s.second);Text(s.third,style=MaterialTheme.typography.labelSmall)}
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun HistoryScreen(c:DesktopController) {
    var tab by remember { mutableStateOf(0) }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(selected=tab==0,onClick={tab=0},label={Text("Notícias (${c.newsDb.listNews(2000).size})")})
            FilterChip(selected=tab==1,onClick={tab=1},label={Text("Vídeos (${c.videoDb.listAll(2000).size})")})
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick={if(tab==0)c.clearNewsHistory() else c.clearVideoHistory()}){Icon(Icons.Default.Delete,null);Spacer(Modifier.width(4.dp));Text("Limpar histórico")}
        }
        if(tab==0) {
            val all=c.newsDb.listNews(2000)
            LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.weight(1f)){items(all,key={it.link}){NewsCard(it)}}
        } else {
            val all=c.videoDb.listAll(2000)
            LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.weight(1f)){items(all,key={it.link}){VideoCard(it)}}
        }
    }
}

@Composable
private fun SettingsScreen(c:DesktopController) {
    var auto by remember { mutableStateOf(c.automaticMonitoring) }
    var interval by remember { mutableIntStateOf(c.newsIntervalMinutes) }
    LazyColumn(verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("Monitoramento automático",style=MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Switch(auto,onCheckedChange={auto=it;c.automaticMonitoring=it})
                        Spacer(Modifier.width(10.dp));Text(if(auto)"Ativo — funciona enquanto o app estiver na bandeja" else "Pausado")
                    }
                    Text("Intervalo automático de notícias")
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        listOf(15,30,60,120).forEach { m -> FilterChip(selected=interval==m,onClick={interval=m;c.newsIntervalMinutes=m},label={Text("$m min")}) }
                    }
                    Text("Demandas: 1 hora • Vídeos: 08h, 12h, 15h, 19h e 21h.")
                }
            }
        }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("Dados portáteis",style=MaterialTheme.typography.titleMedium)
                    Text("Banco, termos, demandas, histórico e preferências ficam na pasta:")
                    SelectionContainer { Text(c.context.filesDir.absolutePath) }
                    Text("Copiar a pasta inteira do programa preserva também os dados desta versão Windows.")
                }
            }
        }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("Compatibilidade funcional v4.0.2",style=MaterialTheme.typography.titleMedium)
                    Text("O Windows usa o mesmo motor de Notícias/Vídeos da versão Android: catálogos, Globoplay, Jarvis, YouTube, telejornais regionais, cruzamento local de Termos/Demandas, histórico, deduplicação e regra de NOVO por primeira captura.")
                    Text("A camada Android de WorkManager/AlarmManager foi substituída por agendamento residente na bandeja do Windows.")
                }
            }
        }
    }
}

private fun openUrl(url:String) {
    runCatching { if(AwtDesktop.isDesktopSupported()) AwtDesktop.getDesktop().browse(URI(url)) }
}

private fun formatDate(ms:Long):String = if(ms<=0L)"—" else SimpleDateFormat("dd/MM/yyyy HH:mm",Locale("pt","BR")).format(Date(ms))
