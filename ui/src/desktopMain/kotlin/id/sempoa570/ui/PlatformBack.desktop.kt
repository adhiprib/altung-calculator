package id.sempoa570.ui

import androidx.compose.runtime.Composable

/** Desktop tidak punya tombol kembali; Esc menutup panduan lewat pemetaan keyboard. */
@Composable
actual fun PlatformBackHandler(onBack: () -> Unit) = Unit
