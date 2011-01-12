/*
 * jPSXdec: PlayStation 1 Media Decoder/Converter in Java
 * Copyright (C) 2007-2010  Michael Sabin
 * All rights reserved.
 *
 * Redistribution and use of the jPSXdec code or any derivative works are
 * permitted provided that the following conditions are met:
 *
 *  * Redistributions may not be sold, nor may they be used in commercial
 *    or revenue-generating business activities.
 *
 *  * Redistributions that are modified from the original source must
 *    include the complete source code, including the source code for all
 *    components used by a binary built from the modified sources. However, as
 *    a special exception, the source code distributed need not include
 *    anything that is normally distributed (in either source or binary form)
 *    with the major components (compiler, kernel, and so on) of the operating
 *    system on which the executable runs, unless that component itself
 *    accompanies the executable.
 *
 *  * Redistributions must reproduce the above copyright notice, this list
 *    of conditions and the following disclaimer in the documentation and/or
 *    other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package jpsxdec.discitems.psxvideoencode;


import jpsxdec.psxvideo.encode.MdecEncoder;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import javax.imageio.ImageIO;
import jpsxdec.cdreaders.CdFileSectorReader;
import jpsxdec.formats.RgbIntImage;
import jpsxdec.discitems.savers.FrameDemuxer;
import jpsxdec.sectors.IVideoSector;
import jpsxdec.psxvideo.PsxYCbCrImage;
import jpsxdec.psxvideo.bitstreams.BitStreamCompressor;
import jpsxdec.psxvideo.bitstreams.BitStreamUncompressor;
import jpsxdec.psxvideo.encode.ParsedMdecImage;
import jpsxdec.psxvideo.mdec.DecodingException;
import jpsxdec.psxvideo.encode.ParsedMdecImage.Block;
import jpsxdec.psxvideo.encode.ParsedMdecImage.MacroBlock;
import jpsxdec.psxvideo.mdec.MdecDecoder_double;
import jpsxdec.psxvideo.mdec.idct.StephensIDCT;
import jpsxdec.util.FeedbackStream;
import jpsxdec.util.NotThisTypeException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class ReplaceFramePartial extends ReplaceFrame {

    private static final boolean DEBUG = false;

    public static final String XML_TAG_NAME = "partial-replace";

    private int _iTolerance;
    private File _imageMaskFile;
    private Rectangle _rectMask;

    public ReplaceFramePartial(Element element) {
        super(element);
        if (element.hasAttribute("tolerance")) {
            setTolerance(element.getAttribute("tolerance"));
        }
        if (element.hasAttribute("mask")) {
            setImageMaskFile(element.getAttribute("mask"));
        }
        if (element.hasAttribute("rect")) {
            setRectMask(element.getAttribute("rect"));
        }
    }
    @Override
    public Element serialize(Document document) {
        Element node = document.createElement(XML_TAG_NAME);
        node.setAttribute("frame", String.valueOf(getFrame()));
        node.setTextContent(getImageFile().toString());
        if (getFormat() != null)
            node.setAttribute("format", getFormat());
        if (_iTolerance > 0)
            node.setAttribute("tolerance", String.valueOf(_iTolerance));
        if (_imageMaskFile != null)
            node.setAttribute("mask", _rectMask.toString());
        if (_rectMask != null)
            node.setAttribute("rect", String.format("%d,%d,%d,%d",
                    _rectMask.x, _rectMask.y, _rectMask.width, _rectMask.height));
        return node;
    }


    public ReplaceFramePartial(String sFrameNumber) {
        super(sFrameNumber);
    }
    public ReplaceFramePartial(int iFrame) {
        super(iFrame);
    }

    public File getImageMaskFile() {
        return _imageMaskFile;
    }

    public void setImageMaskFile(String sImageMaskFile) {
        setImageFile(new File(sImageMaskFile));
    }
    public void setImageMaskFile(File imageMaskFile) {
        _imageMaskFile = imageMaskFile;
    }

    public int getTolerance() {
        return _iTolerance;
    }

    public void setTolerance(String sToleranceValue) {
        setTolerance(Integer.parseInt(sToleranceValue));
    }
    public void setTolerance(int iTolerance) {
        _iTolerance = iTolerance;
    }

    public Rectangle getRectMask() {
        return _rectMask;
    }


    public void setRectMask(String sRectMask) {
        String[] asCoords = sRectMask.trim().split("\\D+");
        setRectMask(new Rectangle(
                Integer.parseInt(asCoords[0]),
                Integer.parseInt(asCoords[1]),
                Integer.parseInt(asCoords[2]),
                Integer.parseInt(asCoords[3])));
    }
    public void setRectMask(Rectangle rectMask) {
        _rectMask = rectMask;
    }

    @Override
    public void replace(FrameDemuxer demuxer, CdFileSectorReader cd, FeedbackStream fbs) throws IOException, DecodingException, NotThisTypeException {
        final int WIDTH = demuxer.getWidth();
        final int HEIGHT = demuxer.getHeight();

        // 1. Parse original image
        byte[] origBits = new byte[demuxer.getDemuxSize()];
        demuxer.copyDemuxData(origBits);
        BitStreamUncompressor uncompressor =
                BitStreamUncompressor.identifyUncompressor(origBits, demuxer.getFrame());
        uncompressor.reset(origBits);
        ParsedMdecImage parsedOrig = new ParsedMdecImage(WIDTH, HEIGHT);
        parsedOrig.readFrom(uncompressor);

        // 2. convert both to RGB
        MdecDecoder_double decoder = new MdecDecoder_double(new StephensIDCT(),
                                                            WIDTH, HEIGHT);
        decoder.decode(parsedOrig.getStream());
        RgbIntImage rgb = new RgbIntImage(WIDTH, HEIGHT);
        decoder.readDecodedRgb(rgb.getWidth(), rgb.getHeight(), rgb.getData(), 0, rgb.getWidth());
        BufferedImage origImg = rgb.toBufferedImage();
        BufferedImage newImg = ImageIO.read(getImageFile());

        // 3. compare the macroblocks, considering the tolerance
        //    and filter any that don't have any differences within
        //    the bounding box and mask
        ArrayList<Point> diffMacblks = findDiffMacroblocks(origImg, newImg);

        if (diffMacblks.size() == 0) {
            fbs.println("No differences found, skipping.");
            return;
        } else if (diffMacblks.size() == ParsedMdecImage.calculateMacroBlocks(WIDTH, HEIGHT)) {
            fbs.printlnWarn("Warning: Entire frame has is different.");
        }

        fbs.println("Found " + diffMacblks.size() + " different macroblocks (16x16):");
        for (Point macblk : diffMacblks) {
            fbs.println(String.format("(%d, %d) ", macblk.x, macblk.y));
        }
        fbs.println();

        PsxYCbCrImage newPsxImg = new PsxYCbCrImage(newImg);
        BitStreamCompressor compressor = uncompressor.makeCompressor();
        ParsedMdecImage parsedNew = new ParsedMdecImage(WIDTH, HEIGHT);
        byte[] abNewDemux;

        int iLuminQscale = parsedOrig.getLuminQscale();
        int iChromQscale = parsedOrig.getChromQscale();
        while (true) {
            fbs.println("Trying qscale luma: " + iLuminQscale + " chroma: " + iChromQscale);

            // 4. Encode replacement image with original frame qscale
            MdecEncoder encoder = new MdecEncoder(newPsxImg, iLuminQscale, iChromQscale);

            // 5. Replace new macroblocks in the parsed source image
            parsedNew.readFrom(encoder.getStream());
            for (Point macblk : diffMacblks) {
                MacroBlock origMacblk = parsedOrig.getMacroBlock(macblk.x, macblk.y);
                MacroBlock newMacblk = parsedNew.getMacroBlock(macblk.x, macblk.y);
                copyMacroBlock(origMacblk, newMacblk, fbs);
            }

            // 6. Recompress the entire original image with the new macroblocks
            abNewDemux = compressor.compress(parsedOrig.getStream(),
                    parsedOrig.getLuminQscale(), parsedOrig.getChromQscale(),
                    parsedOrig.getMdecCodeCount());

            // 7. Check if it will fit
            if (abNewDemux.length <= demuxer.getDemuxSize()) {
                System.out.format("  New demux size %d <= max source %d ",
                        abNewDemux.length, demuxer.getDemuxSize());
                System.out.println();
                break;
            } else {
                System.out.format("  >>> New demux size %d > max source %d <<<",
                        abNewDemux.length, demuxer.getDemuxSize());
                System.out.println();
            }

            // 8. If not, start again from step 4, increasing the qscale
            if (compressor.separateQscales()) {
                if (iLuminQscale == iChromQscale)
                    iChromQscale++;
                else if (iLuminQscale < iChromQscale) {
                    iLuminQscale++;
                    iChromQscale--;
                } else {
                    iChromQscale++;
                }
            } else {
                iLuminQscale++;
                iChromQscale++;
            }
        }

        // 9. replace the frame
        int iDemuxOfs = 0;
        for (int i = 0; i < demuxer.getChunksInFrame(); i++) {
            IVideoSector vidSector = demuxer.getChunk(i);
            iDemuxOfs += vidSector.replaceFrameData(cd, abNewDemux, iDemuxOfs,
                    parsedOrig.getLuminQscale(), parsedOrig.getChromQscale(),
                    parsedOrig.getMdecCodeCount());
        }

    }

    private ArrayList<Point> findDiffMacroblocks(BufferedImage origImg, BufferedImage newImg)
              throws IOException
    {
        int iMacblkWidth  = (origImg.getWidth()  + 15) / 16;
        int iMacblkHeight = (origImg.getHeight() + 15) / 16;

        ArrayList<Point> diffMacblks = new ArrayList<Point>(iMacblkWidth * iMacblkHeight);

        BufferedImage maskImg = null;
        if (_imageMaskFile != null) {
            maskImg = ImageIO.read(_imageMaskFile);
        }

        Point macblk = new Point();
        // 1. make a list of all the macro blocks
        for (int y=0; y<iMacblkHeight; y++) {
            for (int x=0; x<iMacblkWidth; x++) {
                macblk.setLocation(x, y);
                if (blockIsDifferent(macblk, origImg, newImg, maskImg)) {
                    diffMacblks.add(macblk);
                    macblk = new Point();
                }
            }
        }

        return diffMacblks;
    }
    
    private boolean blockIsDifferent(Point macblk, BufferedImage bi1, BufferedImage bi2, BufferedImage maskImg) {

        // 2. filter out macroblocks that aren't touched by the bounding box
        if (_rectMask != null) {
            if (macblk.x * 16+15 < _rectMask.getMinX() ||
                macblk.x * 16    > _rectMask.getMaxX() ||
                macblk.y * 16+15 < _rectMask.getMinY() ||
                macblk.y * 16    > _rectMask.getMaxY())
                return false;
        }

        int iStartX = macblk.x * 16, iStartY = macblk.y * 16;
        for (int y=iStartY; y<iStartY+16; y++) {
            for (int x=iStartX; x<iStartX+16; x++) {
                // 2. filter out pixels that aren't in the bounding box
                if (_rectMask != null) {
                    if (!_rectMask.contains(x, y))
                        continue;
                }
                // 3. filter out pixels that aren't in the bitmap mask
                if (maskImg != null) {
                    if ((maskImg.getRGB(x, y) & 0xffffff) == 0)
                        continue;
                }
                // finally compare the pixels
                int iRgb1 = bi1.getRGB(x, y), iRgb2 = bi2.getRGB(x, y);
                int iDiffR = ((iRgb1 >> 16) & 0xff) - ((iRgb2 >> 16) & 0xff);
                int iDiffG = ((iRgb1 >>  8) & 0xff) - ((iRgb2 >>  8) & 0xff);
                int iDiffB = ((iRgb1      ) & 0xff) - ((iRgb2      ) & 0xff);
                if (Math.abs(iDiffR) > _iTolerance ||
                    Math.abs(iDiffG) > _iTolerance ||
                    Math.abs(iDiffB) > _iTolerance)
                    return true;
            }
        }

        return false;
    }

    private void copyMacroBlock(MacroBlock origMacblk, MacroBlock newMacblk, FeedbackStream fbs) {
        for (int iBlock = 0; iBlock < 6; iBlock++) {
            Block srcBlk = origMacblk.getBlockCopy(iBlock);
            Block modBlk = newMacblk.getBlockCopy(iBlock);
            if (srcBlk.getQscale() != modBlk.getQscale()) {
                if (DEBUG) {
                    fbs.printlnMore(String.format("  %s qscale is different (%d -> %d) ",
                                    srcBlk.getName(), modBlk.getQscale(), srcBlk.getQscale()));
                }

                modBlk.changeQuantizationScale(srcBlk.getQscale());
            }
            origMacblk.replaceBlock(modBlk);
        }
    }

}