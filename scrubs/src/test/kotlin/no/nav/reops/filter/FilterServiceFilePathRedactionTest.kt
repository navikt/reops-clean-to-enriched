package no.nav.reops.filter

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FilterServiceFilePathRedactionTest {

    private fun filterService(): FilterService = FilterService(SimpleMeterRegistry())

    private fun redact(input: String): String {
        val service = filterService()
        return service.filterEvent(TestEventFactory.eventWithData(input)).payload.data!!.get("text").asString()
    }

    // --- Windows paths ---

    @Test
    fun `redacts windows path with backslashes`() {
        assertEquals("[PROXY-FILEPATH]", redact("C:\\Users\\PersonName\\Documents\\secret.txt"))
    }

    @Test
    fun `redacts windows path with different drive letter`() {
        assertEquals("[PROXY-FILEPATH]", redact("D:\\Projects\\private\\data.xlsx"))
    }

    @Test
    fun `redacts windows UNC path`() {
        assertEquals("[PROXY-FILEPATH]", redact("\\\\ServerName\\Share\\folder\\file.docx"))
    }

    @Test
    fun `redacts windows path with forward slashes`() {
        assertEquals("[PROXY-FILEPATH]", redact("C:/Users/JohnDoe/Desktop/private_file.txt"))
    }

    @Test
    fun `redacts windows path with spaces`() {
        assertEquals("[PROXY-FILEPATH]", redact("C:\\Program Files\\My App\\config.ini"))
    }

    // --- Unix paths ---

    @Test
    fun `redacts unix home path`() {
        assertEquals("[PROXY-FILEPATH]", redact("/home/username/Documents/private.pdf"))
    }

    @Test
    fun `redacts unix var log path`() {
        assertEquals("[PROXY-FILEPATH]", redact("/var/log/user_12345678901.log"))
    }

    @Test
    fun `redacts unix usr local path`() {
        assertEquals("[PROXY-FILEPATH]", redact("/usr/local/share/sensitive_data.csv"))
    }

    @Test
    fun `redacts unix home path with extension`() {
        assertEquals("[PROXY-FILEPATH]", redact("/home/username/Documents/file.txt"))
    }

    @Test
    fun `redacts unix hidden file path`() {
        assertEquals("[PROXY-FILEPATH]", redact("/home/john/.ssh/id_rsa"))
    }

    // --- macOS paths ---

    @Test
    fun `redacts macos users library path`() {
        assertEquals("[PROXY-FILEPATH]", redact("/Users/PersonName/Library/ApplicationSupport/app.db"))
    }

    @Test
    fun `redacts macos users desktop path`() {
        assertEquals("[PROXY-FILEPATH]", redact("/Users/john.doe/Desktop/confidential.pages"))
    }

    @Test
    fun `redacts macos volumes path`() {
        assertEquals("[PROXY-FILEPATH]", redact("/Volumes/ExternalDrive/Backup/data.zip"))
    }

    // --- URL-encoded paths ---

    @Test
    fun `redacts file protocol path`() {
        assertEquals("[PROXY-FILEPATH]", redact("file:///C:/Users/John%20Doe/Documents/file.pdf"))
    }

    @Test
    fun `redacts unix path for url encoded test`() {
        assertEquals("[PROXY-FILEPATH]", redact("/home/user/folder/data.json"))
    }

    // --- Relative paths ---

    @Test
    fun `redacts dot-slash relative path`() {
        assertEquals("[PROXY-FILEPATH]", redact("./users/PersonName/config.yml"))
    }

    @Test
    fun `redacts dot-dot-slash relative path`() {
        assertEquals("[PROXY-FILEPATH]", redact("../PersonalFolder/private.db"))
    }

    @Test
    fun `redacts tilde-slash relative path`() {
        assertEquals("[PROXY-FILEPATH]", redact("~/Documents/taxes_2024.pdf"))
    }

    // --- Mixed content ---

    @Test
    fun `redacts windows filepath embedded in sentence`() {
        val result = redact("Error loading file C:\\Users\\Admin\\secret.txt")
        assertTrue(result.contains("[PROXY-FILEPATH]"))
        assertTrue(result.contains("Error loading"))
    }

    @Test
    fun `redacts unix filepath embedded in sentence`() {
        val result = redact("Check /home/personalname/.config/app.conf for settings")
        assertTrue(result.contains("[PROXY-FILEPATH]"))
        assertTrue(result.contains("Check"))
        assertTrue(result.contains("for settings"))
    }

    @Test
    fun `redacts filepath in query parameter`() {
        assertEquals("?file=[PROXY-FILEPATH]", redact("?file=/var/www/users/JohnDoe/uploads/doc.pdf"))
    }

    // --- Common patterns ---

    @Test
    fun `redacts downloads path`() {
        assertEquals("[PROXY-FILEPATH]", redact("/home/john/Downloads/passport_scan.jpg"))
    }

    @Test
    fun `redacts windows pictures path`() {
        assertEquals("[PROXY-FILEPATH]", redact("C:\\Users\\Mary\\Pictures\\ID_card.png"))
    }

    @Test
    fun `redacts backup path`() {
        assertEquals("[PROXY-FILEPATH]", redact("/backup/users/ole_hansen/2024-03-15.tar.gz"))
    }

    @Test
    fun `redacts windows program data path`() {
        assertEquals("[PROXY-FILEPATH]", redact("C:\\ProgramData\\Application\\Users\\PersonName\\cache.dat"))
    }

    // --- Special characters ---

    @Test
    fun `redacts path with hyphens in directory names`() {
        assertEquals("[PROXY-FILEPATH]", redact("/home/user-name/docs/report_2024.pdf"))
    }

    @Test
    fun `redacts windows path with dots in username`() {
        assertEquals("[PROXY-FILEPATH]", redact("C:\\Users\\user.name\\AppData\\Local\\temp.log"))
    }

    @Test
    fun `redacts path with underscores and numbers`() {
        assertEquals("[PROXY-FILEPATH]", redact("/var/log/user_12345/app_log_2024.txt"))
    }

    // --- Edge cases ---

    @Test
    fun `redacts very long windows path`() {
        assertEquals(
            "[PROXY-FILEPATH]",
            redact("C:\\Users\\Administrator\\Very\\Long\\Path\\With\\Many\\Nested\\Directories\\And\\Personal\\Info\\document.docx")
        )
    }

    @Test
    fun `redacts path with multiple extensions`() {
        assertEquals("[PROXY-FILEPATH]", redact("/home/user/backup.tar.gz.enc"))
    }

    @Test
    fun `redacts network path with ip`() {
        val result = redact("\\\\192.168.1.100\\shared\\PersonName\\data.xlsx")
        assertTrue(result.contains("[PROXY-FILEPATH]") || result.contains("[PROXY-IP]"))
    }

    @Test
    fun `redacts hidden config path`() {
        assertEquals("[PROXY-FILEPATH]", redact("/home/user/.config"))
    }

    @Test
    fun `redacts git config path`() {
        assertEquals("[PROXY-FILEPATH]", redact("/repo/.git/config"))
    }

    @Test
    fun `redacts single file at root`() {
        assertEquals("[PROXY-FILEPATH]", redact("/file.txt"))
    }

    // --- Android and iOS ---

    @Test
    fun `redacts android data path`() {
        assertEquals("[PROXY-FILEPATH]", redact("/data/data/com.example.app/files/user_data.db"))
    }

    @Test
    fun `redacts ios var mobile path`() {
        assertEquals("[PROXY-FILEPATH]", redact("/var/mobile/Containers/Data/Application/GUID/Documents/file.txt"))
    }

    // --- Web server ---

    @Test
    fun `redacts var www html path`() {
        assertEquals("[PROXY-FILEPATH]", redact("/var/www/html/uploads/user123/document.pdf"))
    }

    @Test
    fun `redacts srv http path`() {
        assertEquals("[PROXY-FILEPATH]", redact("/srv/http/public/media/private/photo.jpg"))
    }

    @Test
    fun `redacts nginx log path`() {
        assertEquals("[PROXY-FILEPATH]", redact("/var/log/nginx/access.log"))
    }

    // --- Generic Unix ---

    @Test
    fun `redacts custom application path`() {
        assertEquals("[PROXY-FILEPATH]", redact("/custom/application/data/userfile.db"))
    }

    @Test
    fun `redacts app storage path`() {
        assertEquals("[PROXY-FILEPATH]", redact("/app/storage/uploads/document.pdf"))
    }

    @Test
    fun `redacts media path`() {
        assertEquals("[PROXY-FILEPATH]", redact("/media/external/PersonalPhotos/vacation.jpg"))
    }

    @Test
    fun `redacts mount path`() {
        assertEquals("[PROXY-FILEPATH]", redact("/mount/nas/private/secrets.txt"))
    }

    @Test
    fun `redacts docker volumes path`() {
        assertEquals("[PROXY-FILEPATH]", redact("/docker/volumes/app_data/config.yml"))
    }

    @Test
    fun `redacts opt path`() {
        assertEquals("[PROXY-FILEPATH]", redact("/opt/myapp/logs/error.log"))
    }

    // --- File paths vs URLs ---

    @Test
    fun `does not redact single component path`() {
        assertEquals("Visit /help for more info", redact("Visit /help for more info"))
    }

    @Test
    fun `redacts multi-component path`() {
        assertEquals("[PROXY-FILEPATH]", redact("/api/v1/users/profile"))
    }

    @Test
    fun `preserves full URL with protocol`() {
        val input = "https://example.com/api/v1/users/profile"
        assertEquals(input, redact(input))
    }

    @Test
    fun `preserves URL-like string with TLD`() {
        val input = "example.com/api/v1/users/profile"
        assertEquals(input, redact(input))
    }
}

