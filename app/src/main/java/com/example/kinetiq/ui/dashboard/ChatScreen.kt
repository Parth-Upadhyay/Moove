package com.example.kinetiq.ui.dashboard

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.kinetiq.models.Message
import com.example.kinetiq.models.MessageType
import com.example.kinetiq.viewmodel.ChatViewModel
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    otherUserId: String,
    otherUserName: String,
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var messageText by remember { mutableStateOf("") }
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    val listState = rememberLazyListState()

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.sendFile(it, "image.jpg", MessageType.PHOTO) }
    }

    val docLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.sendFile(it, "document.pdf", MessageType.DOCUMENT) }
    }

    LaunchedEffect(otherUserId) {
        viewModel.setPatient(otherUserId)
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(otherUserName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (state.error != null) {
                Text(
                    text = state.error ?: "An error occurred",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(state.messages) { message ->
                    MessageBubble(
                        message = message,
                        isCurrentUser = message.senderId == currentUserId
                    )
                }
            }

            if (state.isUploading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(8.dp)
                        .navigationBarsPadding()
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var showAttachmentMenu by remember { mutableStateOf(false) }

                    Box {
                        IconButton(onClick = { showAttachmentMenu = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Attach")
                        }
                        DropdownMenu(
                            expanded = showAttachmentMenu,
                            onDismissRequest = { showAttachmentMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Image") },
                                onClick = {
                                    showAttachmentMenu = false
                                    imageLauncher.launch("image/*")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Document") },
                                onClick = {
                                    showAttachmentMenu = false
                                    docLauncher.launch("application/*")
                                }
                            )
                        }
                    }

                    TextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text("Type a message...") },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                    IconButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                viewModel.sendMessage(messageText)
                                messageText = ""
                            }
                        },
                        enabled = messageText.isNotBlank()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (messageText.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: Message, isCurrentUser: Boolean) {
    val alignment = if (isCurrentUser) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (isCurrentUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isCurrentUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = if (isCurrentUser) {
        RoundedCornerShape(16.dp, 16.dp, 0.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp)
    }

    val timeString = remember(message.timestamp) {
        if (message.timestamp != null) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(message.timestamp)
        } else {
            "..."
        }
    }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Column(horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start) {
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(bubbleColor)
                    .padding(vertical = 8.dp, horizontal = 12.dp)
                    .widthIn(max = 280.dp)
            ) {
                Column {
                    when (message.type) {
                        MessageType.PHOTO -> {
                            AsyncImage(
                                model = message.fileUrl,
                                contentDescription = "Image message",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        MessageType.DOCUMENT -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(4.dp)
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, tint = contentColor)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = message.fileName ?: "Document",
                                    color = contentColor,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        else -> {
                            Text(
                                text = message.content,
                                color = contentColor,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
            Text(
                text = timeString,
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
            )
        }
    }
}
