<?php
$apiBase = getenv('API_BASE_URL') ?: 'http://java-backend:8080';
$code = $_GET['c'] ?? '';
if ($code === '') {
http_response_code(400);
die('Missing short code');
}
$ctx = stream_context_create([
'http' => ['timeout' => 5, 'ignore_errors' => true],
]);
$response = @file_get_contents($apiBase . '/api/resolve?code=' . urlencode($code), false, $ctx);
if ($response === false) {
http_response_code(502);
die('Could not reach shortener API');
}
$data = json_decode($response, true);
$url = $data['url'] ?? null;
if (!$url) {
http_response_code(404);
die('Short link "' . htmlspecialchars($code) . '" not found');
}
// Write to the SAME shared log file the Java backend writes to. This
// file lives on the "shared-logs" NFS volume, mounted into both
// containers — a local named volume could not do this, since it only
// exists on one container/host at a time.
$logDir = getenv('LOG_DIR') ?: '/var/log/tinylink';
$logLine = date('c') . " [php-frontend] redirected short:$code -> $url" . PHP_EOL;
@file_put_contents($logDir . '/access.log', $logLine, FILE_APPEND | LOCK_EX);
header('Location: ' . $url, true, 302);
exit;
