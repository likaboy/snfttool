/*
 * Copyright 2011 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.typography.font.tools.sfnttool;

import com.google.typography.font.sfntly.Font;
import com.google.typography.font.sfntly.FontFactory;
import com.google.typography.font.sfntly.Tag;
import com.google.typography.font.sfntly.data.WritableFontData;
import com.google.typography.font.sfntly.table.core.CMapTable;
import com.google.typography.font.tools.conversion.eot.EOTWriter;
import com.google.typography.font.tools.conversion.woff.WoffWriter;
import com.google.typography.font.tools.subsetter.HintStripper;
import com.google.typography.font.tools.subsetter.RenumberingSubsetter;
import com.google.typography.font.tools.subsetter.Subsetter;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Raph Levien
 */
public class SfntTool {
    private boolean strip = false;
    private String subsetString = null;
    private boolean woff = false;
    private boolean eot = false;
    private boolean mtx = false;

    private static final long SIZE_THRESHOLD = 2_048_000L;
    private static final long BIG_FILE_SIZE_THRESHOLD = 32_768L;

    public static void main(String[] args) throws IOException {
        SfntTool tool = new SfntTool();
        File fontFile = null;
        File outputFile = null;
        boolean bench = false;
        int nIters = 1;

        for (int i = 0; i < args.length; i++) {
            String option = null;
            if (args[i].charAt(0) == '-') {
                option = args[i].substring(1);
            }

            if (option != null) {
                if (option.equals("help") || option.equals("?")) {
                    printUsage();
                    System.exit(0);
                } else if (option.equals("b") || option.equals("bench")) {
                    nIters = 10000;
                } else if (option.equals("h") || option.equals("hints")) {
                    tool.strip = true;
                } else if (option.equals("s") || option.equals("string")) {
                    File file = new File(args[i + 1]);
                    tool.subsetString = readFileContents(file, StandardCharsets.UTF_8);
                    i++;
                    // tool.subsetString = args[i + 1];
                    // i++;
                } else if (option.equals("w") || option.equals("woff")) {
                    tool.woff = true;
                } else if (option.equals("e") || option.equals("eot")) {
                    tool.eot = true;
                } else if (option.equals("x") || option.equals("mtx")) {
                    tool.mtx = true;
                } else {
                    printUsage();
                    System.exit(1);
                }
            } else {
                if (fontFile == null) {
                    fontFile = new File(args[i]);
                } else {
                    outputFile = new File(args[i]);
                    break;
                }
            }
        }

        if (tool.woff && tool.eot) {
            System.out.println("WOFF and EOT options are mutually exclusive");
            System.exit(1);
        }

        if (fontFile != null && outputFile != null) {
            tool.subsetFontFile(fontFile, outputFile, nIters);
        } else {
            printUsage();
        }
    }

    private static final void printUsage() {
        System.out.println("Subset [-?|-h|-help] [-b] [-s string] fontfile outfile");
        System.out.println("Prototype font subsetter");
        System.out.println("\t-?,-help\tprint this help information");
        System.out.println("\t-s,-string\t String to subset");
        System.out.println("\t-b,-bench\t Benchmark (run 10000 iterations)");
        System.out.println("\t-h,-hints\t Strip hints");
        System.out.println("\t-w,-woff\t Output WOFF format");
        System.out.println("\t-e,-eot\t Output EOT format");
        System.out.println("\t-x,-mtx\t Enable Microtype Express compression for EOT format");
    }

    public void subsetFontFile(File fontFile, File outputFile, int nIters)
            throws IOException {
        FontFactory fontFactory = FontFactory.getInstance();
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(fontFile);
            byte[] fontBytes = new byte[(int) fontFile.length()];
            fis.read(fontBytes);
            Font[] fontArray = null;
            fontArray = fontFactory.loadFonts(fontBytes);
            Font font = fontArray[0];
            List<CMapTable.CMapId> cmapIds = new ArrayList<CMapTable.CMapId>();
            cmapIds.add(CMapTable.CMapId.WINDOWS_BMP);
            byte[] newFontData = null;
            for (int i = 0; i < nIters; i++) {
                Font newFont = font;
                if (subsetString != null) {
                    Subsetter subsetter = new RenumberingSubsetter(newFont, fontFactory);
                    subsetter.setCMaps(cmapIds, 1);
                    List<Integer> glyphs = GlyphCoverage.getGlyphCoverage(font, subsetString);
                    subsetter.setGlyphs(glyphs);
                    Set<Integer> removeTables = new HashSet<Integer>();
                    // Most of the following are valid tables, but we don't renumber them yet, so strip
                    removeTables.add(Tag.GDEF);
                    removeTables.add(Tag.GPOS);
                    removeTables.add(Tag.GSUB);
                    removeTables.add(Tag.kern);
                    removeTables.add(Tag.hdmx);
                    removeTables.add(Tag.vmtx);
                    removeTables.add(Tag.VDMX);
                    removeTables.add(Tag.LTSH);
                    removeTables.add(Tag.DSIG);
                    removeTables.add(Tag.vhea);
                    // AAT tables, not yet defined in sfntly Tag class
                    removeTables.add(Tag.intValue(new byte[]{'m', 'o', 'r', 't'}));
                    removeTables.add(Tag.intValue(new byte[]{'m', 'o', 'r', 'x'}));
                    subsetter.setRemoveTables(removeTables);
                    newFont = subsetter.subset().build();
                }
                if (strip) {
                    Subsetter hintStripper = new HintStripper(newFont, fontFactory);
                    Set<Integer> removeTables = new HashSet<Integer>();
                    removeTables.add(Tag.fpgm);
                    removeTables.add(Tag.prep);
                    removeTables.add(Tag.cvt);
                    removeTables.add(Tag.hdmx);
                    removeTables.add(Tag.VDMX);
                    removeTables.add(Tag.LTSH);
                    removeTables.add(Tag.DSIG);
                    removeTables.add(Tag.vhea);
                    hintStripper.setRemoveTables(removeTables);
                    newFont = hintStripper.subset().build();
                }

                FileOutputStream fos = new FileOutputStream(outputFile);
                if (woff) {
                    WritableFontData woffData = new WoffWriter().convert(newFont);
                    woffData.copyTo(fos);
                } else if (eot) {
                    WritableFontData eotData = new EOTWriter(mtx).convert(newFont);
                    eotData.copyTo(fos);
                } else {
                    fontFactory.serializeFont(newFont, fos);
                }
            }
        } finally {
            if (fis != null) {
                fis.close();
            }
        }
    }

    /**
     * 读取文件内容
     * 分阶段读取
     *
     * @param file
     * @param charset
     * @return
     * @throws IOException
     */
    public static String readFileContents(File file, Charset charset) throws IOException {
        Path path = file.toPath();
        String content;

        if (file.length() <= SIZE_THRESHOLD) {
            content = Files.readString(path, charset);
        } else {
            StringBuilder stringBuilder = new StringBuilder((int) Math.min(file.length(), Integer.MAX_VALUE));
            try (BufferedReader reader = Files.newBufferedReader(path, charset),BIG_FILE_SIZE_THRESHOLD) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stringBuilder.append(line)
                }
                content = stringBuilder.toString();
            }
        }
        return content;
    }
}
