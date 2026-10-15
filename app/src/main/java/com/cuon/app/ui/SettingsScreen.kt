package com.cuon.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuon.app.data.local.FrequentPlaceEntity
import com.cuon.app.data.remote.AmapPoiService
import com.cuon.app.util.openInAmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val AppleBlue = Color(0xFF007AFF)
private val AppleRed = Color(0xFFFF3B30)

/**
 * 设置页：默认城市 + 常用地点簿（注入 AI 上下文），数据/权限入口占位。
 * 不直接依赖 ViewModel，状态与回调由上层注入。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    defaultCity: String,
    frequentPlaces: List<FrequentPlaceEntity>,
    onBack: () -> Unit,
    onSetDefaultCity: (String) -> Unit,
    onUpsertPlace: (FrequentPlaceEntity) -> Unit,
    onDeletePlace: (FrequentPlaceEntity) -> Unit,
    onExportBackup: () -> Unit = {},
    onImportBackup: () -> Unit = {}
) {
    // 正在编辑的地点；id=0 表示新增
    var editingPlace by remember { mutableStateOf<FrequentPlaceEntity?>(null) }

    // 系统权限状态：每次回到前台（从系统设置页返回）重算
    val appContext = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val notificationsEnabled = remember(resumeTick) {
        androidx.core.app.NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    }
    val exactAlarmsEnabled = remember(resumeTick) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            (appContext.getSystemService(android.content.Context.ALARM_SERVICE) as? android.app.AlarmManager)
                ?.canScheduleExactAlarms() ?: false
        } else true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("设置", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── AI 上下文区 ──────────────────────────────
            SettingsSectionHeader(
                title = "让 AI 更懂你",
                subtitle = "这些信息会注入 AI 解析上下文，「回家」「去灵隐寺」等指代会自动补全地点"
            )

            DefaultCityCard(defaultCity = defaultCity, onSave = onSetDefaultCity)

            FrequentPlacesCard(
                places = frequentPlaces,
                onAdd = { editingPlace = FrequentPlaceEntity(id = 0, label = "", address = "", city = defaultCity) },
                onEdit = { editingPlace = it },
                onDelete = onDeletePlace
            )

            // ── 数据与权限区 ──────────────────────────────
            SettingsSectionHeader(title = "数据与系统", subtitle = null)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Storage, contentDescription = null, tint = AppleBlue, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("数据备份与恢复", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "导出 JSON 备份，可分享保存或换机导入",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onExportBackup) { Text("导出", fontSize = 13.sp) }
                    TextButton(onClick = onImportBackup) { Text("导入", fontSize = 13.sp) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                PermissionRow(
                    title = "通知权限",
                    subtitle = "到期提醒、日程确认通知",
                    granted = notificationsEnabled,
                    onClick = {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, appContext.packageName)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        appContext.startActivity(intent)
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                PermissionRow(
                    title = "精确闹钟",
                    subtitle = "保证提醒按分钟准时触发",
                    granted = exactAlarmsEnabled,
                    onClick = {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            val intent = android.content.Intent(
                                android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                android.net.Uri.parse("package:${appContext.packageName}")
                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            appContext.startActivity(intent)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    editingPlace?.let { place ->
        PlaceEditSheet(
            place = place,
            onDismiss = { editingPlace = null },
            onSave = { saved ->
                onUpsertPlace(saved)
                editingPlace = null
            }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String, subtitle: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    action: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            action?.invoke()
        }
        content()
    }
}

/** 默认城市：本地输入态 + 保存按钮（避免逐字符回写 prefs） */
@Composable
private fun DefaultCityCard(defaultCity: String, onSave: (String) -> Unit) {
    var input by remember(defaultCity) { mutableStateOf(defaultCity) }
    val dirty = input.trim() != defaultCity.trim() && input.isNotBlank()

    SectionCard(title = "默认城市") {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            placeholder = { Text("如：杭州") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppleBlue,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        )
        Text(
            text = "省略城市的表达（如「下周去灵隐寺」）将按此城市定位",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (dirty) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { input = defaultCity }) { Text("取消") }
                Button(
                    onClick = { onSave(input) },
                    colors = ButtonDefaults.buttonColors(containerColor = AppleBlue, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("保存") }
            }
        } else if (defaultCity.isNotBlank()) {
            Text(
                text = "当前生效：$defaultCity",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = AppleBlue
            )
        }
    }
}

@Composable
private fun FrequentPlacesCard(
    places: List<FrequentPlaceEntity>,
    onAdd: () -> Unit,
    onEdit: (FrequentPlaceEntity) -> Unit,
    onDelete: (FrequentPlaceEntity) -> Unit
) {
    SectionCard(
        title = "常用地点",
        action = {
            IconButton(onClick = onAdd) {
                Icon(Icons.Outlined.Add, contentDescription = "添加地点", tint = AppleBlue, modifier = Modifier.size(20.dp))
            }
        }
    ) {
        if (places.isEmpty()) {
            Text(
                text = "还没有添加地点。添加「家」「公司」后，说「提醒我晚上7点回家吃饭」就能自动定位家庭地址",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            places.forEach { place ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onEdit(place) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AppleBlue.copy(alpha = 0.08f),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, AppleBlue.copy(alpha = 0.2f))
                    ) {
                        Text(
                            text = place.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Medium,
                                color = AppleBlue
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = place.address,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (place.city.isNotBlank()) {
                            Text(
                                text = place.city,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { onDelete(place) }) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = if (granted) "已开启" else "去开启",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Medium,
                color = if (granted) Color(0xFF34C759) else AppleRed
            )
        )
    }
}

/** 地点编辑弹层：标签 + 城市 + 地址（高德 POI 防抖联想回填） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceEditSheet(
    place: FrequentPlaceEntity,
    onDismiss: () -> Unit,
    onSave: (FrequentPlaceEntity) -> Unit
) {
    var label by remember(place.id) { mutableStateOf(place.label) }
    var city by remember(place.id) { mutableStateOf(place.city) }
    var address by remember(place.id) { mutableStateOf(place.address) }

    val context = LocalContext.current
    val poiService = remember { AmapPoiService() }
    val scope = rememberCoroutineScope()
    var suggestions by remember { mutableStateOf<List<AmapPoiService.PoiResult>>(emptyList()) }
    var showSuggestions by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun onAddressInput(input: String) {
        searchJob?.cancel()
        if (input.isBlank()) {
            suggestions = emptyList()
            showSuggestions = false
            return
        }
        showSuggestions = true
        searchJob = scope.launch {
            delay(350)
            suggestions = try {
                poiService.searchPois(input)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = if (place.id == 0L) "添加常用地点" else "编辑地点",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 快捷标签
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("家", "公司", "孩子学校").forEach { quick ->
                    FilterChip(
                        selected = label == quick,
                        onClick = { label = quick },
                        label = { Text(quick, fontSize = 12.sp) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("标签") },
                placeholder = { Text("如：家 / 公司 / 爸妈家 / 健身房") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppleBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )
            Spacer(modifier = Modifier.height(10.dp))

            Box {
                OutlinedTextField(
                    value = address,
                    onValueChange = {
                        address = it
                        onAddressInput(it)
                    },
                    label = { Text("地址") },
                    placeholder = { Text("输入关键词搜索地址") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = AppleBlue) },
                    trailingIcon = {
                        if (address.isNotBlank()) {
                            IconButton(onClick = { openInAmap(context, address) }) {
                                Icon(Icons.Outlined.Navigation, contentDescription = "在高德地图打开", tint = AppleBlue)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppleBlue,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )
                DropdownMenu(
                    expanded = showSuggestions && suggestions.isNotEmpty(),
                    onDismissRequest = { showSuggestions = false },
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .heightIn(max = 280.dp)
                ) {
                    suggestions.take(6).forEach { poi ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = poi.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val sub = poi.toDisplayAddress()
                                    if (sub != poi.name) {
                                        Text(
                                            text = sub,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            },
                            onClick = {
                                address = poi.toDisplayAddress()
                                if (city.isBlank()) {
                                    city = poi.cityname?.removeSuffix("市").orEmpty()
                                }
                                showSuggestions = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = city,
                onValueChange = { city = it },
                label = { Text("城市") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppleBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    onSave(place.copy(label = label.trim(), address = address.trim(), city = city.trim()))
                },
                enabled = label.isNotBlank() && address.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AppleBlue, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (place.id == 0L) "添加地点" else "保存修改", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
