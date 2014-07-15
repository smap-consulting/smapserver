<?php
$f_name = $_POST['csv_name'];

header("Content-type: application/octet-stream");
header("Content-Disposition: attachment; filename=\"" . $f_name . ".csv\"");

echo $_POST['csv_text'];
?>

