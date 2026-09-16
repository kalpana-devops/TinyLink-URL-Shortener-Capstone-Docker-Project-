<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>TinyLink</title>
<style>
body { font-family: sans-serif; max-width: 480px; margin: 80px auto; text-align: center; }
input[type=url] { width: 320px; padding: 8px; }
button { padding: 8px 16px; }
</style>
</head>
<body>
<h1>TinyLink</h1>
<p>Paste a long URL, get a short one back.</p>
<form action="submit.php" method="POST">
<input type="url" name="url" placeholder="https://example.com/very/long/link" required>
<button type="submit">Shorten</button>
</form>
<p style="margin-top:32px;"><a href="logs.php">View shared NFS access log &rarr;</a></p>
</body>
</html>
