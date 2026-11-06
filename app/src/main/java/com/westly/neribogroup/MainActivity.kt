package com.westly.neribogroup

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.westly.neribogroup.feature.appdetail.AppDashboardScreen
import com.westly.neribogroup.feature.group.AppsScreen
import com.westly.neribogroup.feature.group.CompanyCostsScreen
import com.westly.neribogroup.feature.group.GroupDashboardScreen
import com.westly.neribogroup.feature.group.RenewalsScreen
import com.westly.neribogroup.feature.group.StaffListScreen
import com.westly.neribogroup.feature.staff.StaffDetailScreen
import com.westly.neribogroup.ui.theme.NeriboTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NeriboTheme {
                NeriboRoot()
            }
        }
    }
}

@Composable
private fun NeriboRoot(vm: AppViewModel = viewModel()) {
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            val ok = runCatching {
                val stream = context.contentResolver.openOutputStream(uri)
                    ?: error("Could not open the file")
                stream.use { it.write(vm.exportJson().toByteArray(Charsets.UTF_8)) }
            }.isSuccess
            Toast.makeText(
                context,
                if (ok) "Backup saved" else "Backup failed",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                }
            }.getOrNull()
            val ok = text != null && vm.importJson(text)
            Toast.makeText(
                context,
                if (ok) "Backup restored" else "That file is not a valid backup",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val openApp = vm.openAppId?.let { id -> vm.ledger.apps.firstOrNull { it.id == id } }
    val openStaff = vm.openStaffId?.let { id -> vm.ledger.staff.firstOrNull { it.id == id } }

    if (openApp != null) {
        AppDashboardScreen(
            app = openApp,
            onBack = { vm.openAppId = null },
            onSave = { vm.saveApp(it) },
            onDelete = { vm.deleteApp(openApp.id) }
        )
    } else if (openStaff != null) {
        StaffDetailScreen(
            staff = openStaff,
            onBack = { vm.openStaffId = null },
            onSave = { vm.saveStaff(it) },
            onPay = { vm.payStaff(openStaff.id) },
            onDelete = { vm.deleteStaff(openStaff.id) }
        )
    } else {
        when (vm.openSection) {
            Section.APPS -> AppsScreen(
                ledger = vm.ledger,
                onBack = { vm.openSection = null },
                onOpenApp = { vm.openAppId = it },
                onSave = { vm.saveLedger(it) }
            )
            Section.RENEWALS -> RenewalsScreen(
                ledger = vm.ledger,
                onBack = { vm.openSection = null },
                onRenew = { vm.renew(it) },
                onSave = { vm.saveLedger(it) }
            )
            Section.STAFF -> StaffListScreen(
                ledger = vm.ledger,
                onBack = { vm.openSection = null },
                onOpenStaff = { vm.openStaffId = it },
                onPayAllStaff = { vm.payAllStaff() },
                onSave = { vm.saveLedger(it) }
            )
            Section.COSTS -> CompanyCostsScreen(
                ledger = vm.ledger,
                onBack = { vm.openSection = null },
                onSave = { vm.saveLedger(it) }
            )
            null -> GroupDashboardScreen(
                ledger = vm.ledger,
                onOpenSection = { vm.openSection = it },
                onOpenApp = { vm.openAppId = it },
                onOpenStaff = { vm.openStaffId = it },
                onSave = { vm.saveLedger(it) },
                onExport = { exportLauncher.launch("neribo-group-backup.json") },
                onImport = { importLauncher.launch(arrayOf("*/*")) }
            )
        }
    }
}
