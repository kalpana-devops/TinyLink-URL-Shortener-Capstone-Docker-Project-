<?php
// Reads the SAME shared access.log that both this PHP container and the
// Java backend container write to, over the NFS-backed "shared-logs"
// volume. If you see lines tagged both [php-frontend] and [java-backend]
// interleaved here, that's the proof NFS storage is genuinely shared
// between independent containers — not just persisted for one of them.
$logDir = getenv('LOG_DIR') ?: '/var/log/tinylink';
$logFile = $logDir . '/access.log';
$lines = [];
if (file_exists($logFile)) {
$lines = array_filter(explode(PHP_EOL, file_get_contents($logFile)));
$lines = array_reverse($lines); // newest first
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>TinyLink — Shared NFS Log</title>
<style>
body { font-family: sans-serif; max-width: 720px; margin: 40px auto; }
.line { font-family: monospace; font-size: 12.5px; padding: 4px 8px; border-bottom: 1px
solid #eee; }
.java { color: #b45309; }
.php { color: #2563eb; }
.empty { color: #6b7280; }
</style>
</head>
<body>
<h1>Shared Access Log</h1>
<p>Read from <code><?= htmlspecialchars($logFile) ?></code> — written to by
<strong>both</strong> the PHP frontend and Java backend containers,
via the same NFS-backed volume.</p>
<?php if (empty($lines)): ?>
<p class="empty">No entries yet — shorten and then visit a link first.</p>
<?php else: ?>
<?php foreach ($lines as $line): ?>
<?php
$class = str_contains($line, '[java-backend]') ? 'java'
: (str_contains($line, '[php-frontend]') ? 'php' : '');
?>
<div class="line <?= $class ?>"><?= htmlspecialchars($line) ?></div>
<?php endforeach; ?>
<?php endif; ?>
<p><a href="index.php">&larr; Back</a></p>
</body>
</html>
