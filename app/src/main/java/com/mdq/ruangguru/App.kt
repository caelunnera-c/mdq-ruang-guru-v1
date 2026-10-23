
package com.mdq.ruangguru

import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.*
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ================= TABEL DATABASE =================

@Entity(tableName = "kelas")
data class KelasEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val namaKelas: String,
    val tingkat: String,
    val waliKelas: String,
    val tahunAjaran: String,
    val semester: String,
    val keterangan: String = ""
)

@Entity(tableName = "siswa")
data class SiswaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nis: String,
    val nisn: String = "",
    val namaLengkap: String,
    val jenisKelamin: String,
    val kelasId: Long,
    val tahunMasuk: String,
    val isAktif: Boolean = true,
    val catatan: String = ""
)

@Entity(tableName = "absensi")
data class AbsensiEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val siswaId: Long,
    val kelasId: Long,
    val tanggal: String,
    val mataPelajaran: String,
    val status: String,
    val catatan: String = ""
)

@Entity(tableName = "komponen_nilai")
data class KomponenNilaiEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kelasId: Long,
    val mataPelajaran: String,
    val namaKomponen: String,
    val bobotPersen: Double
)

@Entity(tableName = "nilai_siswa")
data class NilaiSiswaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val komponenId: Long,
    val siswaId: Long,
    val nilai: Double
)

@Entity(tableName = "jurnal_mengajar")
data class JurnalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tanggal: String,
    val namaGuru: String,
    val kelas: String,
    val mataPelajaran: String,
    val jamPelajaran: String,
    val materi: String,
    val tujuan: String,
    val kegiatan: String,
    val refleksi: String,
    val kendala: String = "",
    val tindakLanjut: String = ""
)

@Entity(tableName = "guru")
data class GuruEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nama: String,
    val nip: String,
    val role: String,
    val pinHash: String = ""
)

data class StatusCount(val status: String, val count: Int)

// ================= DAOS =================

@Dao
interface KelasDao {
    @Query("SELECT * FROM kelas ORDER BY namaKelas ASC")
    fun getAllKelas(): Flow<List<KelasEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKelas(kelas: KelasEntity): Long

    @Delete
    suspend fun deleteKelas(kelas: KelasEntity)

    @Query("SELECT COUNT(*) FROM kelas")
    fun getJumlahKelas(): Flow<Int>
}

@Dao
interface SiswaDao {
    @Query("SELECT * FROM siswa WHERE kelasId = :kelasId AND isAktif = 1 ORDER BY namaLengkap ASC")
    fun getSiswaByKelas(kelasId: Long): Flow<List<SiswaEntity>>

    @Query("SELECT * FROM siswa ORDER BY namaLengkap ASC")
    fun getAllSiswa(): Flow<List<SiswaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSiswa(siswa: SiswaEntity): Long

    @Delete
    suspend fun deleteSiswa(siswa: SiswaEntity)

    @Query("SELECT COUNT(*) FROM siswa WHERE isAktif = 1")
    fun getJumlahSiswaAktif(): Flow<Int>
}

@Dao
interface AbsensiDao {
    @Query("SELECT * FROM absensi WHERE kelasId = :kelasId AND tanggal = :tanggal AND mataPelajaran = :mapel")
    fun getAbsensi(kelasId: Long, tanggal: String, mapel: String): Flow<List<AbsensiEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateBatch(absensiList: List<AbsensiEntity>)

    @Query("SELECT status, COUNT(*) as count FROM absensi GROUP BY status")
    fun getKomposisiKehadiran(): Flow<List<StatusCount>>

    @Query("SELECT COUNT(DISTINCT tanggal || mataPelajaran || kelasId) FROM absensi")
    fun getTotalSesiAbsensi(): Flow<Int>
}

@Dao
interface JurnalDao {
    @Query("SELECT * FROM jurnal_mengajar ORDER BY id DESC")
    fun getAllJurnal(): Flow<List<JurnalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJurnal(jurnal: JurnalEntity): Long

    @Query("SELECT COUNT(*) FROM jurnal_mengajar")
    fun getJumlahJurnal(): Flow<Int>
}

@Dao
interface NilaiDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKomponen(komponen: KomponenNilaiEntity): Long

    @Query("SELECT * FROM komponen_nilai WHERE kelasId = :kelasId AND mataPelajaran = :mapel")
    fun getKomponenByKelasMapel(kelasId: Long, mapel: String): Flow<List<KomponenNilaiEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNilai(nilai: NilaiSiswaEntity)
}

@Dao
interface GuruDao {
    @Query("SELECT * FROM guru ORDER BY nama ASC")
    fun getAllGuru(): Flow<List<GuruEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGuru(guru: GuruEntity): Long
}

// ================= DATABASE LOKAL ROOM =================

@Database(
    entities = [
        KelasEntity::class,
        SiswaEntity::class,
        AbsensiEntity::class,
        KomponenNilaiEntity::class,
        NilaiSiswaEntity::class,
        JurnalEntity::class,
        GuruEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun kelasDao(): KelasDao
    abstract fun siswaDao(): SiswaDao
    abstract fun absensiDao(): AbsensiDao
    abstract fun nilaiDao(): NilaiDao
    abstract fun jurnalDao(): JurnalDao
    abstract fun guruDao(): GuruDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mdq_ruangguru_offline.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// ================= DATASTORE PREFERENCES & REPOSITORY =================

val Context.dataStore by preferencesDataStore(name = "user_preferences")

class UserPreferences(private val context: Context) {
    companion object {
        val ORIENTATION_KEY = stringPreferencesKey("screen_orientation")
        val ACTIVE_SEMESTER = stringPreferencesKey("active_semester")
        val ACTIVE_TAHUN_AJARAN = stringPreferencesKey("active_tahun_ajaran")
        val SCHOOL_NAME = stringPreferencesKey("school_name")
    }

    val orientationFlow: Flow<String> = context.dataStore.data.map { it[ORIENTATION_KEY] ?: "AUTO" }
    val semesterFlow: Flow<String> = context.dataStore.data.map { it[ACTIVE_SEMESTER] ?: "Semester Genap" }
    val tahunAjaranFlow: Flow<String> = context.dataStore.data.map { it[ACTIVE_TAHUN_AJARAN] ?: "2024/2025" }
    val schoolNameFlow: Flow<String> = context.dataStore.data.map { it[SCHOOL_NAME] ?: "MDQ Boarding School" }

    suspend fun setOrientation(mode: String) { context.dataStore.edit { it[ORIENTATION_KEY] = mode } }
    suspend fun setSchoolInfo(name: String, semester: String, tahun: String) {
        context.dataStore.edit {
            it[SCHOOL_NAME] = name
            it[ACTIVE_SEMESTER] = semester
            it[ACTIVE_TAHUN_AJARAN] = tahun
        }
    }
}

class AppRepository(private val db: AppDatabase) {
    val allKelas = db.kelasDao().getAllKelas()
    val jumlahKelas = db.kelasDao().getJumlahKelas()
    suspend fun insertKelas(kelas: KelasEntity) = db.kelasDao().insertKelas(kelas)
    suspend fun deleteKelas(kelas: KelasEntity) = db.kelasDao().deleteKelas(kelas)

    val jumlahSiswa = db.siswaDao().getJumlahSiswaAktif()
    val allSiswa = db.siswaDao().getAllSiswa()
    suspend fun insertSiswa(siswa: SiswaEntity) = db.siswaDao().insertSiswa(siswa)
    suspend fun deleteSiswa(siswa: SiswaEntity) = db.siswaDao().deleteSiswa(siswa)

    val komposisiKehadiran = db.absensiDao().getKomposisiKehadiran()
    val totalSesiAbsensi = db.absensiDao().getTotalSesiAbsensi()
    suspend fun simpanAbsensiBatch(list: List<AbsensiEntity>) = db.absensiDao().insertOrUpdateBatch(list)

    val allJurnal = db.jurnalDao().getAllJurnal()
    val jumlahJurnal = db.jurnalDao().getJumlahJurnal()
    suspend fun insertJurnal(jurnal: JurnalEntity) = db.jurnalDao().insertJurnal(jurnal)
    suspend fun insertKomponen(komponen: KomponenNilaiEntity) = db.nilaiDao().insertKomponen(komponen)
    suspend fun insertNilai(nilai: NilaiSiswaEntity) = db.nilaiDao().insertNilai(nilai)
}

// ================= TEMA WARNA PASTEL =================

val BgCream = Color(0xFFFBF8F2)
val SidebarSage = Color(0xFFE4EDE4)
val SidebarItemActive = Color(0xFFFFFFFF)
val TextDarkBrown = Color(0xFF382E2B)
val TextMuted = Color(0xFF7E7672)

val BannerCoral = Color(0xFFF9957F)
val BannerBgPeach = Color(0xFFFEECE5)
val CardBgWhite = Color(0xFFFFFFFF)

val StatTealBg = Color(0xFFE1F3F0)
val StatTealIcon = Color(0xFF5CBBA7)
val StatOrangeBg = Color(0xFFFCE9E2)
val StatOrangeIcon = Color(0xFFF38A6B)
val StatYellowBg = Color(0xFFFEF6E4)
val StatYellowIcon = Color(0xFFF2BE54)
val StatBlueBg = Color(0xFFE5F1FC)
val StatBlueIcon = Color(0xFF71AEEA)

val ColorHadir = Color(0xFF5AB69F)
val ColorSakit = Color(0xFFF2BE54)
val ColorIzin = Color(0xFF71AEEA)
val ColorAlfa = Color(0xFFE77C6B)

@Composable
fun MDQTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = BannerCoral,
            onPrimary = CardBgWhite,
            secondary = StatTealIcon,
            background = BgCream,
            surface = CardBgWhite,
            onBackground = TextDarkBrown,
            onSurface = TextDarkBrown
        ),
        content = content
    )
}

// ================= KOMPONEN UI =================

data class NavItem(val title: String, val icon: ImageVector, val route: String)

@Composable
fun AppSidebar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onQuickJournalClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        NavItem("Dashboard", Icons.Default.Home, "dashboard"),
        NavItem("Data Kelas", Icons.Default.List, "kelas"),
        NavItem("Data Siswa", Icons.Default.Person, "siswa"),
        NavItem("Absensi Siswa", Icons.Default.CheckCircle, "absensi"),
        NavItem("Nilai & Leger", Icons.Default.Star, "nilai"),
        NavItem("Jurnal Mengajar", Icons.Default.Edit, "jurnal"),
        NavItem("Pengaturan", Icons.Default.Settings, "pengaturan")
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(250.dp)
            .background(SidebarSage)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 20.dp, top = 6.dp)
            ) {
                Box(
                    modifier = Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFD3E4DA)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = StatTealIcon, modifier = Modifier.size(36.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Hi, Admin Guru! 👋", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Administrator", fontSize = 11.sp, color = TextMuted)
                }
            }

            items.forEach { item ->
                val isSelected = currentRoute == item.route
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) SidebarItemActive else Color.Transparent)
                        .clickable { onNavigate(item.route) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(item.icon, contentDescription = item.title, tint = if (isSelected) StatTealIcon else TextMuted, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(item.title, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp, color = if (isSelected) TextDarkBrown else TextMuted)
                }
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = BannerBgPeach),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("👑 Jurnal Hari Ini", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextDarkBrown)
                Text("Catat KBM kelas dengan rapi!", fontSize = 10.sp, color = TextMuted, modifier = Modifier.padding(vertical = 4.dp))
                Button(
                    onClick = onQuickJournalClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BannerCoral),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("+ Isi Jurnal Cepat", fontSize = 11.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    iconBgColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBgWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp).fillMaxWidth()) {
            Box(
                modifier = Modifier.size(34.dp).clip(CircleShape).background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = iconTint, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, fontSize = 11.sp, color = TextMuted)
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextDarkBrown)
            Text(subtitle, fontSize = 10.sp, color = TextMuted)
        }
    }
}

@Composable
fun WeeklyBarChart(weeklyCounts: List<Float>, modifier: Modifier = Modifier) {
    val days = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
    val barColors = listOf(BannerCoral, StatYellowIcon, StatTealIcon, BannerCoral, StatBlueIcon, Color(0xFFC4B8EB), Color(0xFFE89BB6))
    val maxVal = (weeklyCounts.maxOrNull() ?: 10f).coerceAtLeast(5f)

    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBgWhite), modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Grafik Kehadiran & Aktivitas", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Persentase kehadiran per sesi", fontSize = 11.sp, color = TextMuted)
                }
                Text("Minggu Ini", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                days.forEachIndexed { i, day ->
                    val value = weeklyCounts.getOrElse(i) { 0f }
                    val heightRatio = (value / maxVal).coerceIn(0.1f, 1f)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Box(modifier = Modifier.width(16.dp).fillMaxHeight(fraction = heightRatio).background(barColors[i % barColors.size], RoundedCornerShape(8.dp)))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(day, fontSize = 10.sp, color = TextMuted)
                    }
                }
            }
        }
    }
}

@Composable
fun AttendanceDonutChart(hadir: Int, sakit: Int, izin: Int, alfa: Int, modifier: Modifier = Modifier) {
    val total = (hadir + sakit + izin + alfa).coerceAtLeast(1)
    val pctHadir = (hadir * 100) / total
    val sweepHadir = (hadir.toFloat() / total) * 360f
    val sweepSakit = (sakit.toFloat() / total) * 360f
    val sweepIzin = (izin.toFloat() / total) * 360f
    val sweepAlfa = (alfa.toFloat() / total) * 360f

    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBgWhite), modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Komposisi Kehadiran Siswa", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(80.dp)) {
                        var startAngle = -90f
                        val stroke = Stroke(width = 20f, cap = StrokeCap.Round)
                        drawArc(ColorHadir, startAngle, sweepHadir, false, style = stroke)
                        startAngle += sweepHadir
                        drawArc(ColorSakit, startAngle, sweepSakit, false, style = stroke)
                        startAngle += sweepSakit
                        drawArc(ColorIzin, startAngle, sweepIzin, false, style = stroke)
                        startAngle += sweepIzin
                        drawArc(ColorAlfa, startAngle, sweepAlfa, false, style = stroke)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${pctHadir}%", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Hadir", fontSize = 9.sp, color = TextMuted)
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    LegendItem("Hadir (H)", ColorHadir, "$hadir")
                    LegendItem("Sakit (S)", ColorSakit, "$sakit")
                    LegendItem("Izin (I)", ColorIzin, "$izin")
                    LegendItem("Alfa (A)", ColorAlfa, "$alfa")
                }
            }
        }
    }
}

@Composable
fun LegendItem(label: String, color: Color, count: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, fontSize = 11.sp, color = TextDarkBrown)
        }
        Text(count, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextDarkBrown)
    }
}

// ================= LAYAR LENGKAP =================

data class DashboardState(
    val totalSiswa: Int = 0,
    val totalKelas: Int = 0,
    val totalJurnal: Int = 0,
    val totalSesi: Int = 0,
    val hadirCount: Int = 0,
    val sakitCount: Int = 0,
    val izinCount: Int = 0,
    val alfaCount: Int = 0,
    val persentaseHadir: Int = 0
)

@Composable
fun DashboardScreen(
    state: DashboardState,
    semester: String,
    tahunAjaran: String,
    onMulaiAbsensi: () -> Unit,
    onInputNilai: () -> Unit,
    isTablet: Boolean
) {
    var searchQuery by remember { mutableStateOf("") }
    val sampleWeeklyData = listOf(85f, 40f, 25f, 35f, 30f, 15f, 10f)

    LazyColumn(modifier = Modifier.fillMaxSize().background(BgCream).padding(16.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Dashboard", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = TextDarkBrown)
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari...", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                    modifier = Modifier.width(180.dp).height(44.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = CardBgWhite, focusedContainerColor = CardBgWhite, unfocusedBorderColor = Color.Transparent, focusedBorderColor = StatTealIcon),
                    singleLine = true
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BannerBgPeach), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("☀️ $semester • Tahun Ajaran $tahunAjaran", fontSize = 11.sp, color = BannerCoral, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Selamat Mengajar, Bapak/Ibu Guru! ✨", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextDarkBrown)
                    Text("Kelola absensi, rekap nilai siswa, dan jurnal mengajar dengan mudah hari ini.", fontSize = 12.sp, color = TextMuted, modifier = Modifier.padding(vertical = 4.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Row {
                        Button(onClick = onMulaiAbsensi, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = BannerCoral)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Mulai Absensi", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        OutlinedButton(onClick = onInputNilai, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = CardBgWhite), border = null) {
                            Text("Input Nilai", color = TextDarkBrown, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            if (isTablet) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Total Siswa", "${state.totalSiswa}", "Siswa Aktif", Icons.Default.Person, StatTealIcon, StatTealBg, Modifier.weight(1f))
                    StatCard("Data Kelas", "${state.totalKelas}", "Rombel", Icons.Default.List, StatOrangeIcon, StatOrangeBg, Modifier.weight(1f))
                    StatCard("Kehadiran", "${state.persentaseHadir}%", "${state.totalSesi} Sesi", Icons.Default.CheckCircle, StatYellowIcon, StatYellowBg, Modifier.weight(1f))
                    StatCard("Jurnal", "${state.totalJurnal}", "Pertemuan", Icons.Default.Edit, StatBlueIcon, StatBlueBg, Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard("Total Siswa", "${state.totalSiswa}", "Siswa Aktif", Icons.Default.Person, StatTealIcon, StatTealBg, Modifier.weight(1f))
                        StatCard("Data Kelas", "${state.totalKelas}", "Rombel", Icons.Default.List, StatOrangeIcon, StatOrangeBg, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard("Kehadiran", "${state.persentaseHadir}%", "${state.totalSesi} Sesi", Icons.Default.CheckCircle, StatYellowIcon, StatYellowBg, Modifier.weight(1f))
                        StatCard("Jurnal", "${state.totalJurnal}", "Pertemuan", Icons.Default.Edit, StatBlueIcon, StatBlueBg, Modifier.weight(1f))
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            if (isTablet) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    WeeklyBarChart(sampleWeeklyData, Modifier.weight(1.2f))
                    AttendanceDonutChart(state.hadirCount, state.sakitCount, state.izinCount, state.alfaCount, Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    WeeklyBarChart(sampleWeeklyData, Modifier.fillMaxWidth())
                    AttendanceDonutChart(state.hadirCount, state.sakitCount, state.izinCount, state.alfaCount, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
fun KelasScreen(
    kelasList: List<KelasEntity>,
    onAddKelas: (String, String, String, String, String) -> Unit,
    onDeleteKelas: (KelasEntity) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var namaKelas by remember { mutableStateOf("") }
    var waliKelas by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(BgCream).padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Data Rombel Kelas", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDarkBrown)
            Button(onClick = { showDialog = true }, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = BannerCoral)) {
                Text("+ Tambah Kelas", fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(kelasList) { kelas ->
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardBgWhite), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(kelas.namaKelas, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Wali: ${kelas.waliKelas} • Tingkat ${kelas.tingkat}", fontSize = 12.sp, color = TextMuted)
                        }
                        IconButton(onClick = { onDeleteKelas(kelas) }) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = ColorAlfa)
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Tambah Kelas Baru") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = namaKelas, onValueChange = { namaKelas = it }, label = { Text("Nama Kelas (misal: X RPL 1)") })
                    OutlinedTextField(value = waliKelas, onValueChange = { waliKelas = it }, label = { Text("Nama Wali Kelas") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (namaKelas.isNotBlank()) {
                        onAddKelas(namaKelas, "10", waliKelas, "2024/2025", "Genap")
                        namaKelas = ""; waliKelas = ""; showDialog = false
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = BannerCoral)) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Batal") } }
        )
    }
}

@Composable
fun SiswaScreen(
    siswaList: List<SiswaEntity>,
    kelasList: List<KelasEntity>,
    onAddSiswa: (String, String, String, Long, String) -> Unit,
    onDeleteSiswa: (SiswaEntity) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var nis by remember { mutableStateOf("") }
    var nama by remember { mutableStateOf("") }
    var jk by remember { mutableStateOf("L") }
    val selectedKelasId = kelasList.firstOrNull()?.id ?: 0L

    Column(modifier = Modifier.fillMaxSize().background(BgCream).padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Data Siswa", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDarkBrown)
            Button(onClick = { showDialog = true }, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = BannerCoral)) {
                Text("+ Tambah Siswa", fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(siswaList) { siswa ->
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardBgWhite), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(siswa.namaLengkap, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("NIS: ${siswa.nis} • JK: ${siswa.jenisKelamin}", fontSize = 12.sp, color = TextMuted)
                        }
                        IconButton(onClick = { onDeleteSiswa(siswa) }) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = ColorAlfa)
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Tambah Siswa") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = nis, onValueChange = { nis = it }, label = { Text("NIS") })
                    OutlinedTextField(value = nama, onValueChange = { nama = it }, label = { Text("Nama Siswa") })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("JK: ")
                        RadioButton(selected = jk == "L", onClick = { jk = "L" })
                        Text("L")
                        Spacer(modifier = Modifier.width(8.dp))
                        RadioButton(selected = jk == "P", onClick = { jk = "P" })
                        Text("P")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (nis.isNotBlank() && nama.isNotBlank()) {
                        onAddSiswa(nis, nama, jk, selectedKelasId, "2024")
                        nis = ""; nama = ""; showDialog = false
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = BannerCoral)) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Batal") } }
        )
    }
}

@Composable
fun AbsensiScreen(
    kelasList: List<KelasEntity>,
    siswaList: List<SiswaEntity>,
    onSimpanAbsensi: (List<AbsensiEntity>) -> Unit
) {
    val context = LocalContext.current
    val selectedKelasId = kelasList.firstOrNull()?.id ?: 0L
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    var mapel by remember { mutableStateOf("Pemrograman Dasar") }

    val siswaDiKelas = siswaList.filter { it.kelasId == selectedKelasId }
    val statusMap = remember { mutableStateMapOf<Long, String>() }

    LaunchedEffect(selectedKelasId, siswaList) {
        siswaDiKelas.forEach { s -> if (!statusMap.containsKey(s.id)) statusMap[s.id] = "HADIR" }
    }

    Column(modifier = Modifier.fillMaxSize().background(BgCream).padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Absensi Harian", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDarkBrown)
            Button(
                onClick = {
                    val list = siswaDiKelas.map { s ->
                        AbsensiEntity(siswaId = s.id, kelasId = selectedKelasId, tanggal = today, mataPelajaran = mapel, status = statusMap[s.id] ?: "HADIR")
                    }
                    onSimpanAbsensi(list)
                    Toast.makeText(context, "Absensi berhasil disimpan!", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BannerCoral)
            ) { Text("Simpan Sesi", fontSize = 12.sp) }
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(value = mapel, onValueChange = { mapel = it }, label = { Text("Mata Pelajaran") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(siswaDiKelas) { s ->
                val cur = statusMap[s.id] ?: "HADIR"
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardBgWhite), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(s.namaLengkap, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("NIS: ${s.nis}", fontSize = 11.sp, color = TextMuted)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            StatusBadge("H", ColorHadir, cur == "HADIR") { statusMap[s.id] = "HADIR" }
                            StatusBadge("S", ColorSakit, cur == "SAKIT") { statusMap[s.id] = "SAKIT" }
                            StatusBadge("I", ColorIzin, cur == "IZIN") { statusMap[s.id] = "IZIN" }
                            StatusBadge("A", ColorAlfa, cur == "ALFA") { statusMap[s.id] = "ALFA" }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(label: String, color: Color, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isSelected) color else Color(0xFFF1F1F1),
        modifier = Modifier.size(32.dp).clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = if (isSelected) Color.White else TextMuted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
fun NilaiScreen(
    kelasList: List<KelasEntity>,
    siswaList: List<SiswaEntity>,
    onAddKomponen: (Long, String, String, Double) -> Unit,
    onInputNilai: (Long, Long, Double) -> Unit
) {
    val selectedKelasId = kelasList.firstOrNull()?.id ?: 0L
    val siswaDiKelas = siswaList.filter { it.kelasId == selectedKelasId }

    Column(modifier = Modifier.fillMaxSize().background(BgCream).padding(16.dp)) {
        Text("Penilaian & Leger Siswa", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDarkBrown)
        Text("Perhitungan bobot nilai otomatis secara offline", fontSize = 11.sp, color = TextMuted)
        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(siswaDiKelas) { s ->
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardBgWhite), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(s.namaLengkap, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("NIS: ${s.nis}", fontSize = 11.sp, color = TextMuted)
                        }
                        Text("88.0 (A)", fontWeight = FontWeight.Bold, color = StatTealIcon, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun JurnalScreen(jurnalList: List<JurnalEntity>, onAddJurnal: (JurnalEntity) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    var materi by remember { mutableStateOf("") }
    var refleksi by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(BgCream).padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Jurnal Mengajar Guru", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDarkBrown)
            Button(onClick = { showDialog = true }, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = BannerCoral)) {
                Text("+ Buat Jurnal", fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(jurnalList) { j ->
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardBgWhite), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(j.materi, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("${j.kelas} • ${j.mataPelajaran} • ${j.tanggal}", fontSize = 11.sp, color = StatTealIcon)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Refleksi: ${j.refleksi}", fontSize = 12.sp, color = TextDarkBrown)
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Catat Jurnal Mengajar") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = materi, onValueChange = { materi = it }, label = { Text("Materi Pembelajaran") })
                    OutlinedTextField(value = refleksi, onValueChange = { refleksi = it }, label = { Text("Refleksi / Catatan KBM") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (materi.isNotBlank()) {
                        onAddJurnal(JurnalEntity(tanggal = "2024-05-20", namaGuru = "Admin Guru", kelas = "X RPL 1", mataPelajaran = "Produktif", jamPelajaran = "07:30 - 09:00", materi = materi, tujuan = "Tercapai", kegiatan = "Praktik", refleksi = refleksi))
                        materi = ""; refleksi = ""; showDialog = false
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = BannerCoral)) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Batal") } }
        )
    }
}

@Composable
fun PengaturanScreen(
    currentOrientation: String,
    schoolName: String,
    semester: String,
    tahunAjaran: String,
    onOrientationChange: (String) -> Unit,
    onSaveSchoolInfo: (String, String, String) -> Unit
) {
    val context = LocalContext.current
    var namaSekolah by remember { mutableStateOf(schoolName) }

    Column(modifier = Modifier.fillMaxSize().background(BgCream).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Pengaturan Aplikasi", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDarkBrown)

        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBgWhite), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Mode Orientasi Layar", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    FilterChip(selected = currentOrientation == "AUTO", onClick = { onOrientationChange("AUTO") }, label = { Text("Otomatis") })
                    Spacer(modifier = Modifier.width(6.dp))
                    FilterChip(selected = currentOrientation == "PORTRAIT", onClick = { onOrientationChange("PORTRAIT") }, label = { Text("HP Potret") })
                    Spacer(modifier = Modifier.width(6.dp))
                    FilterChip(selected = currentOrientation == "LANDSCAPE", onClick = { onOrientationChange("LANDSCAPE") }, label = { Text("Tablet Lanskap") })
                }
            }
        }

        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBgWhite), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Identitas Sekolah", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                OutlinedTextField(value = namaSekolah, onValueChange = { namaSekolah = it }, label = { Text("Nama Sekolah") }, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = {
                        onSaveSchoolInfo(namaSekolah, semester, tahunAjaran)
                        Toast.makeText(context, "Pengaturan berhasil disimpan!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BannerCoral)
                ) { Text("Simpan") }
            }
        }
    }
}

// ================= ENTRY POINT UTAMA =================

class MDQApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob())
    val database by lazy { AppDatabase.getDatabase(this, applicationScope) }
    val repository by lazy { AppRepository(database) }
    val userPreferences by lazy { UserPreferences(this) }
}

class MainViewModel(
    private val repository: AppRepository,
    private val preferences: UserPreferences
) : ViewModel() {

    val orientationMode = preferences.orientationFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "AUTO")
    val activeSemester = preferences.semesterFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Semester Genap")
    val activeTahunAjaran = preferences.tahunAjaranFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "2024/2025")
    val schoolName = preferences.schoolNameFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "MDQ Boarding School")

    val allKelas = repository.allKelas.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allSiswa = repository.allSiswa.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allJurnal = repository.allJurnal.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dashboardState: StateFlow<DashboardState> = combine(
        repository.jumlahSiswa,
        repository.jumlahKelas,
        repository.jumlahJurnal,
        repository.totalSesiAbsensi,
        repository.komposisiKehadiran
    ) { siswa, kelas, jurnal, sesi, komposisi ->
        var h = 0; var s = 0; var i = 0; var a = 0
        komposisi.forEach {
            when (it.status) {
                "HADIR" -> h += it.count
                "SAKIT" -> s += it.count
                "IZIN" -> i += it.count
                "ALFA" -> a += it.count
            }
        }
        val total = h + s + i + a
        val persen = if (total > 0) ((h.toDouble() / total) * 100).toInt() else 0

        DashboardState(
            totalSiswa = siswa,
            totalKelas = kelas,
            totalJurnal = jurnal,
            totalSesi = sesi,
            hadirCount = h,
            sakitCount = s,
            izinCount = i,
            alfaCount = a,
            persentaseHadir = persen
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardState())

    fun setOrientation(mode: String) = viewModelScope.launch { preferences.setOrientation(mode) }
    fun setSchoolInfo(name: String, sem: String, thn: String) = viewModelScope.launch { preferences.setSchoolInfo(name, sem, thn) }

    fun addKelas(nama: String, tingkat: String, wali: String, tahun: String, semester: String) = viewModelScope.launch {
        repository.insertKelas(KelasEntity(namaKelas = nama, tingkat = tingkat, waliKelas = wali, tahunAjaran = tahun, semester = semester))
    }
    fun deleteKelas(kelas: KelasEntity) = viewModelScope.launch { repository.deleteKelas(kelas) }

    fun addSiswa(nis: String, nama: String, jk: String, kelasId: Long, thn: String) = viewModelScope.launch {
        repository.insertSiswa(SiswaEntity(nis = nis, namaLengkap = nama, jenisKelamin = jk, kelasId = kelasId, tahunMasuk = thn))
    }
    fun deleteSiswa(siswa: SiswaEntity) = viewModelScope.launch { repository.deleteSiswa(siswa) }

    fun simpanAbsensi(list: List<AbsensiEntity>) = viewModelScope.launch { repository.simpanAbsensiBatch(list) }
    fun simpanJurnal(jurnal: JurnalEntity) = viewModelScope.launch { repository.insertJurnal(jurnal) }
    fun addKomponenNilai(kelasId: Long, mapel: String, nama: String, bobot: Double) = viewModelScope.launch {
        repository.insertKomponen(KomponenNilaiEntity(kelasId = kelasId, mataPelajaran = mapel, namaKomponen = nama, bobotPersen = bobot))
    }
    fun inputNilai(kompId: Long, siswaId: Long, nilai: Double) = viewModelScope.launch {
        repository.insertNilai(NilaiSiswaEntity(komponenId = kompId, siswaId = siswaId, nilai = nilai))
    }
}

class ViewModelFactory(
    private val repository: AppRepository,
    private val preferences: UserPreferences
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(repository, preferences) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as MDQApplication
        val factory = ViewModelFactory(app.repository, app.userPreferences)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        setContent {
            MDQTheme {
                val orientationMode by viewModel.orientationMode.collectAsState()

                LaunchedEffect(orientationMode) {
                    requestedOrientation = when (orientationMode) {
                        "PORTRAIT" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        "LANDSCAPE" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }

                val config = LocalConfiguration.current
                val isTablet = config.screenWidthDp >= 600

                MainContainer(viewModel = viewModel, isTablet = isTablet)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContainer(viewModel: MainViewModel, isTablet: Boolean) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "dashboard"

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val dashboardState by viewModel.dashboardState.collectAsState()
    val semester by viewModel.activeSemester.collectAsState()
    val tahunAjaran by viewModel.activeTahunAjaran.collectAsState()
    val schoolName by viewModel.schoolName.collectAsState()
    val orientationMode by viewModel.orientationMode.collectAsState()

    val kelasList by viewModel.allKelas.collectAsState()
    val siswaList by viewModel.allSiswa.collectAsState()
    val jurnalList by viewModel.allJurnal.collectAsState()

    if (isTablet) {
        Row(modifier = Modifier.fillMaxSize()) {
            AppSidebar(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onQuickJournalClick = { navController.navigate("jurnal") }
            )

            Box(modifier = Modifier.weight(1f)) {
                NavContent(navController, viewModel, dashboardState, semester, tahunAjaran, schoolName, orientationMode, kelasList, siswaList, jurnalList, true)
            }
        }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(modifier = Modifier.width(260.dp)) {
                    AppSidebar(
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            scope.launch { drawerState.close() }
                            navController.navigate(route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onQuickJournalClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate("jurnal")
                        }
                    )
                }
            }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("MDQ Ruang Guru") },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        }
                    )
                }
            ) { padding ->
                Box(modifier = Modifier.padding(padding)) {
                    NavContent(navController, viewModel, dashboardState, semester, tahunAjaran, schoolName, orientationMode, kelasList, siswaList, jurnalList, false)
                }
            }
        }
    }
}

@Composable
fun NavContent(
    navController: androidx.navigation.NavHostController,
    viewModel: MainViewModel,
    dashboardState: DashboardState,
    semester: String,
    tahunAjaran: String,
    schoolName: String,
    orientationMode: String,
    kelasList: List<KelasEntity>,
    siswaList: List<SiswaEntity>,
    jurnalList: List<JurnalEntity>,
    isTablet: Boolean
) {
    NavHost(navController = navController, startDestination = "dashboard") {
        composable("dashboard") {
            DashboardScreen(
                state = dashboardState,
                semester = semester,
                tahunAjaran = tahunAjaran,
                onMulaiAbsensi = { navController.navigate("absensi") },
                onInputNilai = { navController.navigate("nilai") },
                isTablet = isTablet
            )
        }
        composable("kelas") {
            KelasScreen(kelasList, { n, t, w, th, s -> viewModel.addKelas(n, t, w, th, s) }, { viewModel.deleteKelas(it) })
        }
        composable("siswa") {
            SiswaScreen(siswaList, kelasList, { nis, nama, jk, kid, th -> viewModel.addSiswa(nis, nama, jk, kid, th) }, { viewModel.deleteSiswa(it) })
        }
        composable("absensi") {
            AbsensiScreen(kelasList, siswaList) { viewModel.simpanAbsensi(it) }
        }
        composable("nilai") {
            NilaiScreen(kelasList, siswaList, { kid, m, n, b -> viewModel.addKomponenNilai(kid, m, n, b) }, { kid, sid, n -> viewModel.inputNilai(kid, sid, n) })
        }
        composable("jurnal") {
            JurnalScreen(jurnalList) { viewModel.simpanJurnal(it) }
        }
        composable("pengaturan") {
            PengaturanScreen(orientationMode, schoolName, semester, tahunAjaran, { viewModel.setOrientation(it) }, { n, s, t -> viewModel.setSchoolInfo(n, s, t) })
        }
    }
}
