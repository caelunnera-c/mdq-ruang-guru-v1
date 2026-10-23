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

// ================= ENTITAS / TABEL DATABASE =================

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

@Entity(
    tableName = "siswa",
    foreignKeys = [
        ForeignKey(
            entity = KelasEntity::class,
            parentColumns = ["id"],
            childColumns = ["kelasId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("nis", unique = true), Index("kelasId")]
)
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

@Entity(
    tableName = "absensi",
    foreignKeys = [
        ForeignKey(
            entity = SiswaEntity::class,
            parentColumns = ["id"],
            childColumns = ["siswaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["siswaId", "tanggal", "mataPelajaran"], unique = true),
        Index("kelasId"),
        Index("tanggal")
    ]
)
data class AbsensiEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val siswaId: Long,
    val kelasId: Long,
    val tanggal: String,
    val mataPelajaran: String,
    val status: String,
    val catatan: String = ""
)

@Entity(
    tableName = "komponen_nilai",
    foreignKeys = [
        ForeignKey(
            entity = KelasEntity::class,
            parentColumns = ["id"],
            childColumns = ["kelasId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("kelasId")]
)
data class KomponenNilaiEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kelasId: Long,
    val mataPelajaran: String,
    val namaKomponen: String,
    val bobotPersen: Double
)

@Entity(
    tableName = "nilai_siswa",
    foreignKeys = [
        ForeignKey(
            entity = KomponenNilaiEntity::class,
            parentColumns = ["id"],
            childColumns = ["komponenId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SiswaEntity::class,
            parentColumns = ["id"],
            childColumns = ["siswaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["komponenId", "siswaId"], unique = true),
        Index("siswaId")
    ]
)
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

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSiswa(siswa: SiswaEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBatch(siswaList: List<SiswaEntity>)

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
                .addCallback(DatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(private val scope: CoroutineScope) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        val k1 = KelasEntity(namaKelas = "X RPL 1", tingkat = "10", waliKelas = "Ahmad S.Kom", tahunAjaran = "2024/2025", semester = "Genap")
                        val idK1 = database.kelasDao().insertKelas(k1)
                        database.siswaDao().insertSiswa(SiswaEntity(nis = "1001", namaLengkap = "Budi Santoso", jenisKelamin = "L", kelasId = idK1, tahunMasuk = "2024"))
                        database.siswaDao().insertSiswa(SiswaEntity(nis = "1002", namaLengkap = "Anisa Putri", jenisKelamin = "P", kelasId = idK1, tahunMasuk = "2024"))
                        database.guruDao().insertGuru(GuruEntity(nama = "Admin Kepala Sekolah", nip = "19780101", role = "ADMIN"))
                    }
                }
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
    suspend fun insertSiswaBatch(list: List<SiswaEntity>) = db.siswaDao().insertBatch(list)
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

// ================= WARNA PASTEL & TEMA =================

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

val MDQTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = TextDarkBrown),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDarkBrown),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextDarkBrown),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, color = TextDarkBrown),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp, color = TextMuted)
)

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
        typography = MDQTypography,
        content = content
    )
}

// ================= KOMPONEN UI & GRAFIK =================

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
        NavItem("Data Kelas", Icons.Default.MeetingRoom, "kelas"),
        NavItem("Data Siswa", Icons.Default.Person, "siswa"),
        NavItem("Absensi Siswa", Icons.Default.CheckCircleOutline, "absensi"),
        NavItem("Nilai & Leger", Icons.Default.Grade, "nilai"),
        NavItem("Jurnal Mengajar", Icons.Default.MenuBook, "jurnal"),
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
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextDark
