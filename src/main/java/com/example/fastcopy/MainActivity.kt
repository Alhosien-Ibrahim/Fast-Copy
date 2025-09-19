package com.example.fastcopy

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import com.example.fastcopy.components.LineManager
import com.example.fastcopy.service.FloatingService
import com.example.fastcopy.storage.DataStoreManager
import com.example.fastcopy.ui.theme.FastCopyTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                val context = LocalContext.current
                val store = remember { DataStoreManager(context) }
                val scope = rememberCoroutineScope()
                val darkThemeEnabled = isSystemInDarkTheme()

                FastCopyTheme(darkTheme = darkThemeEnabled) {
                    FastCopyScreen(
                        store = store,
                        darkTheme = darkThemeEnabled
                    )
                }
            }
        }
    }
}

@Composable
fun FastCopyScreen(
    store: DataStoreManager,
    darkTheme: Boolean
) {
    var floatingEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        store.isFloatingEnabled.collectLatest {
            floatingEnabled = it
        }
    }

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scaffoldState = rememberScaffoldState()
    val haptics = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    val manager = remember { mutableStateOf(LineManager()) }
    var statusText by remember { mutableStateOf("لا توجد بيانات") }
    var progress by remember { mutableStateOf(0f) }
    var showButtons by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var lineNumberInput by remember { mutableStateOf("") }
    var hasConfirmed by remember { mutableStateOf(false) }

    // استعادة البيانات المحفوظة
    LaunchedEffect(Unit) {
        store.inputText.collectLatest {
            if (!hasConfirmed && it.isNotEmpty()) {
                inputText = TextFieldValue(it)
                manager.value.loadLines(it)
                progress = manager.value.progress
                statusText = manager.value.statusText
                showButtons = manager.value.lines.isNotEmpty()
                hasConfirmed = manager.value.lines.isNotEmpty()
            }
        }
    }

    // حفظ الفهرس عند تغييره
    LaunchedEffect(manager.value.index) {
        if (manager.value.lines.isNotEmpty()) {
            store.saveIndex(manager.value.index)
        }
    }

    // استعادة الفهرس المحفوظ
    LaunchedEffect(Unit) {
        store.currentIndex.collectLatest { index ->
            if (manager.value.lines.isNotEmpty() && index in 0 until manager.value.lines.size) {
                manager.value.index = index
                progress = manager.value.progress
                statusText = manager.value.statusText
            }
        }
    }

    LaunchedEffect(statusText) {
        if (statusText.isNotBlank() && statusText != "لا توجد بيانات") {
            scaffoldState.snackbarHostState.showSnackbar(statusText)
        }
    }

    Scaffold(
        scaffoldState = scaffoldState,
        snackbarHost = { SnackbarHost(it) },
        backgroundColor = MaterialTheme.colors.background,
        bottomBar = {
            Surface(
                color = if (darkTheme) MaterialTheme.colors.surface else MaterialTheme.colors.surface.copy(alpha = 0.95f),
                elevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(vertical = 8.dp)
                ) {
                    // عداد السطور
                    Text(
                        text = statusText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (statusText == "لا توجد بيانات")
                            MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        else
                            MaterialTheme.colors.primary
                    )

                    // شريط التقدم
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .padding(horizontal = 16.dp),
                        color = MaterialTheme.colors.primary,
                        backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.15f)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // العنوان الرئيسي
            Text(
                text = "⚡ نسخ سريع سطر بسطر",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                textAlign = TextAlign.Center
            )

            // زر الـ Floating Button Switch
            if (manager.value.lines.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            floatingEnabled = !floatingEnabled
                            scope.launch {
                                if (!Settings.canDrawOverlays(context)) {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:" + context.packageName)
                                    )
                                    context.startActivity(intent)
                                    return@launch
                                }

                                store.saveFloatingEnabled(floatingEnabled)

                                if (floatingEnabled) {
                                    val serviceIntent = Intent(context, FloatingService::class.java)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        context.startForegroundService(serviceIntent)
                                    } else {
                                        context.startService(serviceIntent)
                                    }
                                } else {
                                    val stopIntent = Intent(context, FloatingService::class.java)
                                    context.stopService(stopIntent)
                                }
                            }
                        },
                    backgroundColor = MaterialTheme.colors.surface,
                    elevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "تفعيل الزر العائم",
                            fontSize = 15.sp,
                            color = MaterialTheme.colors.onSurface
                        )
                        Switch(
                            checked = floatingEnabled,
                            onCheckedChange = null, // خليها null عشان الكليك يبقى من الكارت كله
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colors.primary,
                                checkedTrackColor = MaterialTheme.colors.primary.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }

            // حقل إدخال النص بحجم ثابت
            OutlinedTextField(
                value = inputText,
                onValueChange = {
                    inputText = it
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                label = {
                    Text(
                        "أدخل الأرقام، كل رقم في سطر",
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )
                },
                maxLines = Int.MAX_VALUE,
                singleLine = false,
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Default),
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 16.sp,
                    color = MaterialTheme.colors.onSurface
                ),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    backgroundColor = MaterialTheme.colors.surface,
                    cursorColor = MaterialTheme.colors.primary,
                    focusedBorderColor = MaterialTheme.colors.primary,
                    unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.3f)
                )
            )

            val scale by animateFloatAsState(
                targetValue = if (showButtons) 1.03f else 1f,
                label = "scale"
            )

            // أزرار التحكم
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!hasConfirmed) {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .graphicsLayer(scaleX = scale, scaleY = scale),
                        onClick = {
                            if (inputText.text.isNotBlank()) {
                                hasConfirmed = true
                                manager.value.loadLines(inputText.text)
                                if (manager.value.lines.isNotEmpty()) {
                                    scope.launch {
                                        val savedIndex = store.currentIndex.first()
                                        if (savedIndex > 0 && savedIndex < manager.value.lines.size) {
                                            manager.value.index = savedIndex
                                        }

                                        manager.value.currentLine()?.let {
                                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(it))
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            Toast.makeText(context, "✅ تم نسخ السطر ${manager.value.index + 1}", Toast.LENGTH_SHORT).show()
                                        }

                                        store.saveLines(manager.value.lines)
                                        store.saveIndex(manager.value.index)
                                        store.saveInput(inputText.text)
                                    }
                                    progress = manager.value.progress
                                    statusText = manager.value.statusText
                                    showButtons = true
                                } else {
                                    statusText = "⚠️ لم يتم العثور على سطور صالحة"
                                    showButtons = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.primary
                        ),
                        elevation = ButtonDefaults.elevation(
                            defaultElevation = 4.dp,
                            pressedElevation = 8.dp
                        )
                    ) {
                        Text(
                            "✅ تأكيد البيانات",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (showButtons) {
                    // أزرار التنقل
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            onClick = {
                                manager.value.previous()?.let {
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(it))
                                    scope.launch { store.saveIndex(manager.value.index) }
                                    progress = manager.value.progress
                                    statusText = manager.value.statusText
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = MaterialTheme.colors.secondary
                            )
                        ) {
                            Text("↩️ السابق", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }

                        Button(
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            onClick = {
                                manager.value.next()?.let {
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(it))
                                    scope.launch { store.saveIndex(manager.value.index) }
                                    progress = manager.value.progress
                                    statusText = manager.value.statusText
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = MaterialTheme.colors.secondary
                            )
                        ) {
                            Text("📋 التالي", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    // أزرار النسخ الإضافية
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            onClick = {
                                val all = manager.value.copyAll()
                                if (all.isNotBlank()) {
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(all))
                                    Toast.makeText(context, "📑 تم نسخ كل السطور", Toast.LENGTH_SHORT).show()
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                } else {
                                    statusText = "⚠️ لا يوجد بيانات لنسخها"
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                backgroundColor = MaterialTheme.colors.surface
                            )
                        ) {
                            Text("📑 نسخ الكل", fontSize = 14.sp)
                        }

                        OutlinedButton(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            onClick = {
                                showDialog = true
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                backgroundColor = MaterialTheme.colors.surface
                            )
                        ) {
                            Text("📌 نسخ حسب الرقم", fontSize = 14.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // زر إعادة التشغيل
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        onClick = {
                            scope.launch {
                                manager.value.reset()
                                inputText = TextFieldValue("")
                                progress = 0f
                                statusText = "لا توجد بيانات"
                                showButtons = false
                                hasConfirmed = false

                                store.clearAll()
                                store.saveFloatingEnabled(false)
                                val stopIntent = Intent(context, FloatingService::class.java)
                                context.stopService(stopIntent)

                                Toast.makeText(context, "🔄 تم إعادة التشغيل", Toast.LENGTH_SHORT).show()
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.error
                        ),
                        elevation = ButtonDefaults.elevation(
                            defaultElevation = 2.dp,
                            pressedElevation = 6.dp
                        )
                    ) {
                        Text(
                            "🔄 إعادة التشغيل",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colors.onError
                        )
                    }
                }
            }
        }

        // Dialog نسخ حسب الرقم
        if (showDialog) {
            AlertDialog(
                onDismissRequest = {
                    showDialog = false
                    lineNumberInput = ""
                },
                title = {
                    Text(
                        "📌 نسخ حسب الرقم",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    OutlinedTextField(
                        value = lineNumberInput,
                        onValueChange = { lineNumberInput = it },
                        label = { Text("أدخل رقم السطر") },
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            backgroundColor = MaterialTheme.colors.surface,
                            cursorColor = MaterialTheme.colors.primary,
                            focusedBorderColor = MaterialTheme.colors.primary
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val num = lineNumberInput.toIntOrNull()
                        if (num != null && num in 1..manager.value.lines.size) {
                            manager.value.copyByNumber(num)?.let {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(it))
                                scope.launch { store.saveIndex(manager.value.index) }
                                progress = manager.value.progress
                                statusText = manager.value.statusText
                                Toast.makeText(context, "✅ تم نسخ السطر $num", Toast.LENGTH_SHORT).show()
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            showDialog = false
                            lineNumberInput = ""
                        } else {
                            Toast.makeText(context, "❌ رقم غير صالح", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text("تأكيد", fontWeight = FontWeight.Medium)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDialog = false
                        lineNumberInput = ""
                    }) {
                        Text("إلغاء")
                    }
                },
                backgroundColor = MaterialTheme.colors.surface
            )
        }
    }
}