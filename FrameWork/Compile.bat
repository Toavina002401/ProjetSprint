@echo off

rem Chemin vers repertoire de temp
set tempsrc=tempsrc

rem Nom du FrameWork
set Belouh=BelouhFrameWork
set controlleur=controlleur

rem Suppresion des anciens FrameWork dans lib
if exist "lib/%Belouh%.jar" (
    rd /S /Q "lib/%Belouh%.jar"
    echo Le dossier lib/%Belouh%.jar et son contenu ont ete supprimes avec succes.
)

mkdir "%tempsrc%"
echo Le nouveau dossier %tempsrc% a ete cree avec succes.

rem Copie les sources dans le tempsrc
for /r "src" %%f in (*.java) do copy "%%f" "%tempsrc%"

rem Compilation de tous les fichiers Java du répertoire tempsrc
javac -cp "lib/*" -d "." "%tempsrc%\*.java"

rem Decompresser en jar
jar -cf "%Belouh%.jar" "%controlleur%"

rem Suppression du dossier controlleur et tempsrc
if exist "%controlleur%" (
    rd /S /Q "%controlleur%"
    echo Le dossier %controlleur% et son contenu ont ete supprimes avec succes.
)

if exist "%tempsrc%" (
    rd /S /Q "%tempsrc%"
    echo Le dossier %tempsrc% et son contenu ont ete supprimes avec succes.
)

rem Déplace le fichier JAR vers le lib
move "%Belouh%.jar" "lib/"

pause
