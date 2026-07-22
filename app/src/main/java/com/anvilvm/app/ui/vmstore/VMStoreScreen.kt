package com.anvilvm.app.ui.vmstore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class OSImage(
    val name: String,
    val arch: String,
    val size: String,
    val description: String
)

@Composable
fun VMStoreScreen(
    onImageSelected: (OSImage) -> Unit = {}
) {
    val images = remember {
        listOf(
            OSImage("Alpine Linux 3.20", "x86_64", "150 MB", "Lightweight Linux for containers and VMs"),
            OSImage("Arch Linux", "x86_64", "800 MB", "Rolling-release, minimalist Linux"),
            OSImage("Debian 12 (Bookworm)", "x86_64", "1.2 GB", "Stable, general-purpose Linux"),
            OSImage("AegisOS", "x86_64", "2.0 GB", "Cyber warfare OS with hardware isolation"),
            OSImage("Void Linux", "x86_64", "600 MB", "Independent Linux with runit init"),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0B0D))
            .padding(16.dp)
    ) {
        Text(
            text = "VM Image Store",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF00E5FF),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(images) { image ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onImageSelected(image) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1B1D))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = image.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFFFFFFF)
                            )
                            Text(
                                text = image.arch,
                                fontSize = 12.sp,
                                color = Color(0xFF00E5FF)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = image.description,
                            fontSize = 13.sp,
                            color = Color(0xFF888888)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = image.size,
                            fontSize = 12.sp,
                            color = Color(0xFF555555)
                        )
                    }
                }
            }
        }
    }
}
