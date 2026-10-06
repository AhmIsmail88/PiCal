package com.ahmedismail.flowtrack.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import com.ahmedismail.flowtrack.R
import com.ahmedismail.flowtrack.ui.theme.*

@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val version = remember(context) { context.packageManager.getPackageInfo(context.packageName, 0) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.fillMaxWidth().glassPanel().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(painterResource(R.drawable.pical_brand_mark), contentDescription = null, modifier = Modifier.size(96.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Navy)
            Text(stringResource(R.string.about_tagline), style = MaterialTheme.typography.bodyMedium, color = Steel)
            Text(stringResource(R.string.about_version, version.versionName ?: "—", PackageInfoCompat.getLongVersionCode(version).toString()),
                style = MaterialTheme.typography.bodySmall, color = Ink2)
            Text(stringResource(R.string.signature), style = MaterialTheme.typography.bodySmall, color = Ink2)
            Button(onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.linkedin.com/in/ahmed-ismail-soliman")))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.about_contact_unavailable, Toast.LENGTH_LONG).show()
                }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Steel, contentColor = CardWhite)) {
                Text(stringResource(R.string.about_contact_linkedin))
            }
        }
        AboutCard(R.string.about_overview_heading, R.string.about_overview)
        AboutCard(R.string.about_sources_heading, R.string.about_sources)
        AboutCard(R.string.about_calculation_heading, R.string.about_calculation)
        AboutCard(R.string.about_data_heading, R.string.about_data)
    }
}

@Composable
private fun AboutCard(title: Int, body: Int) {
    Column(Modifier.fillMaxWidth().glassPanel().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = Steel)
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = Ink)
    }
}
