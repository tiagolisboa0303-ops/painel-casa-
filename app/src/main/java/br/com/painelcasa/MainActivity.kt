package br.com.painelcasa

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.documentfile.provider.DocumentFile
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideBars()
        setContent { PainelCasaApp() }
    }

    override fun onResume() {
        super.onResume()
        hideBars()
    }

    private fun hideBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

private val Bg = Color(0xFF06131E)
private val Card = Color(0xFF102838)
private val Cyan = Color(0xFF11DDEB)
private val Green = Color(0xFF16E19A)
private val Yellow = Color(0xFFFFC21A)
private val Muted = Color(0xFF9DB5C5)

@Composable
fun PainelCasaApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("painel_casa", Context.MODE_PRIVATE) }

    var showSettings by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var slideshow by remember { mutableStateOf(false) }
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var treeUri by remember { mutableStateOf(prefs.getString("photo_tree", null)?.let(Uri::parse)) }
    var smartHomePackage by remember { mutableStateOf(prefs.getString("smarthome_package", null)) }
    var idleMinutes by remember { mutableIntStateOf(prefs.getInt("idle_minutes", 2)) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            treeUri = uri
            prefs.edit().putString("photo_tree", uri.toString()).apply()
        }
    }

    LaunchedEffect(idleMinutes, slideshow) {
        while (true) {
            delay(1000)
            if (!slideshow && !showSettings && !showAppPicker &&
                System.currentTimeMillis() - lastTouch >= idleMinutes * 60_000L
            ) {
                slideshow = true
            }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, background = Bg, surface = Card)) {
        Box(Modifier.fillMaxSize().background(Bg)) {
            when {
                slideshow -> SlideshowScreen(treeUri) {
                    slideshow = false
                    lastTouch = System.currentTimeMillis()
                }
                showAppPicker -> AppPicker(context, onPick = { pkg ->
                    smartHomePackage = pkg
                    prefs.edit().putString("smarthome_package", pkg).apply()
                    showAppPicker = false
                    showSettings = true
                    lastTouch = System.currentTimeMillis()
                }, onBack = {
                    showAppPicker = false
                    showSettings = true
                })
                showSettings -> SettingsScreen(
                    treeUri = treeUri,
                    smartHomePackage = smartHomePackage,
                    idleMinutes = idleMinutes,
                    onPickFolder = { folderPicker.launch(treeUri) },
                    onPickApp = { showSettings = false; showAppPicker = true },
                    onIdle = {
                        idleMinutes = it
                        prefs.edit().putInt("idle_minutes", it).apply()
                        lastTouch = System.currentTimeMillis()
                    },
                    onBack = { showSettings = false; lastTouch = System.currentTimeMillis() }
                )
                else -> DashboardScreen(
                    context = context,
                    smartHomePackage = smartHomePackage,
                    onPhotos = { slideshow = true },
                    onSettings = { showSettings = true },
                    onTouch = { lastTouch = System.currentTimeMillis() }
                )
            }
        }
    }
}

@Composable
private fun DashboardScreen(
    context: Context,
    smartHomePackage: String?,
    onPhotos: () -> Unit,
    onSettings: () -> Unit,
    onTouch: () -> Unit
) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) { delay(30_000); now = Date() }
    }

    val time = remember(now) { SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(now) }
    val date = remember(now) { SimpleDateFormat("EEE, dd 'de' MMM 'de' yyyy", Locale("pt", "BR")).format(now) }

    Column(Modifier.fillMaxSize().padding(22.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(time, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(date.replaceFirstChar { it.uppercase() }, color = Muted, fontSize = 13.sp)
            }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.Wifi, null, tint = Color.White)
            Spacer(Modifier.width(12.dp))
            IconButton(onClick = { onTouch(); onSettings() }) {
                Icon(Icons.Default.Settings, "Configurações", tint = Color.White)
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MainCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                icon = Icons.Default.WbSunny,
                iconTint = Yellow,
                title = "Energia Solar",
                subtitle = "AUXSOL",
                line1 = "Monitoramento da geração",
                line2 = "Acompanhe sua usina em tempo real",
                onClick = { onTouch(); launchPackage(context, "com.auxsol") }
            )
            MainCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                icon = Icons.Default.Shield,
                iconTint = Green,
                title = "Alarme",
                subtitle = "SmartHome",
                line1 = if (smartHomePackage == null) "Aplicativo ainda não configurado" else "Aplicativo configurado",
                line2 = smartHomePackage ?: "Selecione o SmartHome nas configurações",
                onClick = {
                    onTouch()
                    if (smartHomePackage != null) launchPackage(context, smartHomePackage)
                    else onSettings()
                }
            )
        }

        Spacer(Modifier.height(14.dp))

        Surface(
            color = Card,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().height(112.dp).clickable { onTouch(); onPhotos() }
        ) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PhotoLibrary, null, tint = Cyan, modifier = Modifier.size(46.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Porta-retratos", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Fotos do Google Drive", color = Color.White, fontSize = 15.sp)
                    Text("Inicia automaticamente após o tempo configurado", color = Muted, fontSize = 13.sp)
                }
                Icon(Icons.Default.ChevronRight, null, tint = Cyan, modifier = Modifier.size(34.dp))
            }
        }
    }
}

@Composable
private fun MainCard(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    line1: String,
    line2: String,
    onClick: () -> Unit
) {
    Surface(color = Card, shape = RoundedCornerShape(22.dp), modifier = modifier) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(48.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(subtitle, color = Muted, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(line1, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(line2, color = Muted, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Cyan)
            ) {
                Text("Abrir", color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.ChevronRight, null, tint = Color.White)
            }
        }
    }
}

@Composable
private fun SlideshowScreen(treeUri: Uri?, onExit: () -> Unit) {
    val context = LocalContext.current
    var images by remember(treeUri) { mutableStateOf<List<Uri>>(emptyList()) }
    var index by remember { mutableIntStateOf(0) }

    LaunchedEffect(treeUri) {
        images = treeUri?.let { loadImageUris(context, it) }.orEmpty().shuffled()
        index = 0
    }

    LaunchedEffect(images) {
        while (images.isNotEmpty()) {
            delay(15_000)
            index = (index + 1) % images.size
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onExit),
        contentAlignment = Alignment.Center
    ) {
        if (images.isEmpty()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.PhotoLibrary, null, tint = Cyan, modifier = Modifier.size(72.dp))
                Spacer(Modifier.height(14.dp))
                Text(
                    if (treeUri == null) "Escolha a pasta de fotos nas configurações" else "Nenhuma imagem encontrada nessa pasta",
                    color = Color.White,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )
                Text("Toque para voltar", color = Muted)
            }
        } else {
            key(images[index]) {
                AsyncImage(
                    model = images[index],
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
            Surface(
                color = Color.Black.copy(alpha = 0.35f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
            ) {
                Text(
                    SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date()),
                    color = Color.White,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    treeUri: Uri?,
    smartHomePackage: String?,
    idleMinutes: Int,
    onPickFolder: () -> Unit,
    onPickApp: () -> Unit,
    onIdle: (Int) -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
            Text("Configurações", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))

        SettingCard(
            icon = Icons.Default.Folder,
            title = "Pasta do porta-retratos",
            description = if (treeUri == null) "Nenhuma pasta selecionada" else "Pasta conectada pelo Android/Google Drive",
            action = "Escolher pasta",
            onClick = onPickFolder
        )
        Spacer(Modifier.height(12.dp))
        SettingCard(
            icon = Icons.Default.Shield,
            title = "Aplicativo do alarme",
            description = smartHomePackage ?: "Ainda não configurado",
            action = "Selecionar SmartHome",
            onClick = onPickApp
        )
        Spacer(Modifier.height(12.dp))

        Surface(color = Card, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Text("Tempo para iniciar o porta-retratos", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 5, 10).forEach { min ->
                        FilterChip(
                            selected = idleMinutes == min,
                            onClick = { onIdle(min) },
                            label = { Text("$min min") }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("AUXSOL: com.auxsol", color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun SettingCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    action: String,
    onClick: () -> Unit
) {
    Surface(color = Card, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Cyan, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(description, color = Muted, fontSize = 13.sp)
            }
            TextButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun AppPicker(context: Context, onPick: (String) -> Unit, onBack: () -> Unit) {
    val pm = context.packageManager
    val apps = remember {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
            Text("Selecione o SmartHome", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(apps, key = { it.packageName }) { app ->
                Surface(
                    color = Card,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onPick(app.packageName) }
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(pm.getApplicationLabel(app).toString(), color = Color.White, fontWeight = FontWeight.Bold)
                        Text(app.packageName, color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun loadImageUris(context: Context, treeUri: Uri): List<Uri> {
    val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
    return root.listFiles()
        .filter { it.isFile && (it.type?.startsWith("image/") == true || looksLikeImage(it.name)) }
        .map { it.uri }
}

private fun looksLikeImage(name: String?): Boolean {
    val n = name?.lowercase() ?: return false
    return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp")
}

private fun launchPackage(context: Context, packageName: String) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (launchIntent != null) {
        context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } else {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
