package org.yubox.arduinoplugin;

/*
Todo el código Java mostrado a continuación es el reemplazo de la siguiente regla de Makefile.:

$(YUBOX_PROJECT).tar.gz: data/manifest.txt $(YUBOX_PROJECT).ino.nodemcu-32s.bin
        rm -rf dist/
        mkdir dist
        cp data/* $(YUBOX_PROJECT).ino.nodemcu-32s.bin dist/
        rm -f $(YUBOX_PROJECT).tar.gz
        cd dist && tar -cf ../$(YUBOX_PROJECT).tar * && cd ..
        gzip -9 $(YUBOX_PROJECT).tar
        rm -rf dist/

*/
import java.util.*;
import java.io.*;

import processing.app.PreferencesData;
import processing.app.BaseNoGui;
import processing.app.Editor;
import processing.app.tools.Tool;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
//import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.utils.IOUtils;

public class YUBOXFrameworkTGZOTA
implements Tool
{
    Editor editor;

    public void init(Editor editor) { this.editor = editor; }
    public String getMenuTitle() { return "YUBOX - Build .tar.gz for OTA"; }
    public void run()
    {
        _assembleYuboxOTA();
    }

    private void _assembleYuboxOTA()
    {
        // Verificar que realmente estoy en una plataforma ESP32
        if (!PreferencesData.get("target_platform").contentEquals("esp32")) {
            System.err.println();
            editor.statusError("YUBOX OTA Not Supported on "+PreferencesData.get("target_platform"));
            return;
        }

        // Acumular la lista de recursos que se empaquetarán
        ArrayList<File> ybxContent = new ArrayList<>();
        File sketchDir = editor.getSketch().getFolder();

        // El directorio data/ tiene que existir.
        File dataDir = new File(sketchDir, "data");
        if (!dataDir.exists() || !dataDir.isDirectory()) {
            // Directorio data/ no existe
            String msg = "ERR: Sketch does not contain data directory. Maybe YUBOX Framework assembly needs to be run?";
            System.err.println(msg);
            editor.statusError(msg);
            return;
        }
        File manifest = new File(dataDir, "manifest.txt");
        if (!manifest.exists()) {
            // Archivo manifest.txt no existe. Este data/ no parece ser de YUBOX Framework
            String msg = "ERR: Sketch data directory does not contain manifest.txt. Maybe YUBOX Framework assembly needs to be run?";
            System.err.println(msg);
            editor.statusError(msg);
            return;
        }
        System.out.println("INFO: building list of entries for YUBOX OTA tarball...");
        File[] lsdata = dataDir.listFiles();
        for (File entry : lsdata) {
            if (entry.isFile()) {
                ybxContent.add(entry);
                System.out.println("\t"+entry.getName());
            } else {
                System.err.println("WARN: skipping non-regular entry "+entry.getAbsolutePath());
            }
        }

        // Se debe recoger el nombre del sketch .ino para construir el nombre del proyecto
        // y para construir el nombre del binario a incluir en el tarball
        String ybxProjectName = editor.getSketch().getName();
        lsdata = sketchDir.listFiles();
        boolean exportedBinFound = false;
        for (File entry : lsdata) {
            String fn = entry.getName();

            if (entry.isFile() && fn.startsWith(ybxProjectName+".ino.") && fn.endsWith(".bin")) {
                exportedBinFound = true;
                ybxContent.add(entry);
                System.out.println("\t"+entry.getName());
            }
        }
        if (!exportedBinFound) {
            String msg = "ERR: exported binary not found! Maybe you need to export compiled binary?";
            System.err.println(msg);
            editor.statusError(msg);
            return;
        }

        // Ahora se debe crear el archivo targz y meter los archivos. Pero las entradas no deben tener
        // componentes de directorio, ni siquiera ./
        File tgzOTA = new File(sketchDir, ybxProjectName+".tar.gz");
        try {
            TarArchiveOutputStream tarStream = new TarArchiveOutputStream(new GzipCompressorOutputStream(new FileOutputStream(tgzOTA)));
            for (File entry : ybxContent) {
                tarStream.putArchiveEntry(new TarArchiveEntry(entry, entry.getName()));
                try (FileInputStream in = new FileInputStream(entry)){
                    IOUtils.copy(in, tarStream);
                }
                tarStream.closeArchiveEntry();
            }
            tarStream.close();
        } catch (IOException e) {
            // Uno o más archivos no se pudieron procesar
            System.err.println();
            editor.statusError(e);
            return;
        }

        // Construcción finalizada
        System.out.println();
        System.out.println(String.format(
            "Finished assembling YUBOX OTA tarball %1$s. Now upload it using YUBOX OTA module in your YUBOX Node web interface.",
            tgzOTA.getAbsolutePath()));
    }
}
