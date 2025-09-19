package com.example.fastcopy.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun SuccessDialog(
    message: String,
    onDismiss: () -> Unit
) {
    BaseDialog(
        title = "تم!",
        message = message,
        icon = "✅",
        iconColor = Color(0xFF4CAF50),
        onDismiss = onDismiss
    )
}

@Composable
fun ErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    BaseDialog(
        title = "خطأ!",
        message = message,
        icon = "❌",
        iconColor = Color(0xFFE53935),
        onDismiss = onDismiss
    )
}

@Composable
fun InputNumberDialog(
    title: String = "📌 نسخ حسب الرقم",
    confirmText: String = "تأكيد",
    cancelText: String = "إلغاء",
    onConfirm: (Int) -> Unit,
    onCancel: () -> Unit
) {
    var input by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("أدخل رقم السطر") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = {
                        val number = input.toIntOrNull()
                        if (number != null) onConfirm(number)
                    }) {
                        Text(confirmText)
                    }
                    TextButton(onClick = onCancel) {
                        Text(cancelText)
                    }
                }
            }
        }
    }
}

@Composable
private fun BaseDialog(
    title: String,
    message: String,
    icon: String,
    iconColor: Color,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            elevation = 8.dp,
            color = MaterialTheme.colors.surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = icon, fontSize = 40.sp, color = iconColor)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = message, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onDismiss) {
                    Text("حسنًا")
                }
            }
        }
    }
}