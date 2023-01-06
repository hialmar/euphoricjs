
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.net.*;
import java.util.Arrays;
import javax.sound.sampled.*;

public class Euphoric extends Panel implements WindowListener, Runnable, KeyListener
{
    private final String appletName = "Euphoric-Java 0.2";
    private final boolean EMULATE_SOUND = false;
    private final String TAPE_NAME = "FILLTHEBOX.tap";
    private final int[] palette = { 0xFF000000, 0xFFFF0000, 0xFF00FF00, 0xFFFFFF00, 0xFF0000FF, 0xFFFF00FF, 0xFF00FFFF, 0xFFFFFFFF };

    private Frame frame;

    private long frameTime=0;
    private boolean initialized = false;
    private boolean exit = false;

    private final int[] pixelBuffer = new int[ 240 * 224 ];
    private Image bufferImage;

    private SourceDataLine waveOutA, waveOutB, waveOutC;

    private int scale = 2;
    private int bufferX = 0;
    private int bufferY = 0;
    private boolean drawBorders = true;
    private int frameSkip = 0;

    public static void main(String[] args)
    {
        Euphoric oric = new Euphoric();

        oric.frame = new Frame( oric.appletName );
        oric.frame.addWindowListener( oric );
        oric.setSize(800, 600);
        oric.frame.add( oric );
        oric.frame.pack();
        oric.frame.setSize(800, 600);
        oric.frame.setVisible(true);
    }

//*****************************************************************************************************************
// Interface WindowListener

    public void windowActivated( WindowEvent event ){ requestFocus(); }
    public void windowClosed( WindowEvent event ){ exit = true; System.exit( 0 ); }
    public void windowClosing( WindowEvent event ){ frame.dispose(); }
    public void windowDeactivated( WindowEvent event ){}
    public void windowDeiconified( WindowEvent event ){}
    public void windowIconified( WindowEvent event ){}
    public void windowOpened( WindowEvent event ){ start(); }

//*****************************************************************************************************************
// Interface ImageObserver

    public boolean imageUpdate( Image image, int inforegisterP, int x, int y, int width, int height )
    {
      return inforegisterP != ImageObserver.ALLBITS;
    }

//*****************************************************************************************************************
// Interface KeyListener

    public void keyPressed( KeyEvent event )
    {
        int keycode=event.getKeyCode();
        if( keycode < matrixLocation.length && matrixLocation[ keycode ] >= 0 )
        {
            int line = matrixLocation[ keycode ] >> 3 ;
            int column = matrixLocation[ keycode ] & 7 ;

            if( keycode == KeyEvent.VK_SHIFT && event.getKeyLocation() == KeyEvent.KEY_LOCATION_RIGHT)
            {
                line = 7 ;
                column = 4;
            }
            keyboardMatrix[ line ] |= 1 << column;

            kbdKeyPressed = (keyboardMatrix[ kbdSelectedLine ] & kbdSelectedColumns) != 0 ;
        }
        else
        {
          switch (keycode) {
            case KeyEvent.VK_F4 -> {
              if (frameSkip > 0) frameSkip--;
            }
            case KeyEvent.VK_F5 -> frameSkip++;
            case KeyEvent.VK_F6 -> {    // RESET
              reset = true;
              nmi = true;
            }
            case KeyEvent.VK_F7 ->    // NMI
              nmi = true;
            case KeyEvent.VK_F10 ->   // exit
              exit = true;
            case KeyEvent.VK_F11 -> {   // 1x scale
              scale = 1;
              drawBorders = true;
            }
            case KeyEvent.VK_F12 -> {   // 2x scale
              scale = 2;
              drawBorders = true;
            }
          }
        }
    }


    public void keyReleased( KeyEvent event )
    {
        int keycode=event.getKeyCode();
        if( keycode < matrixLocation.length && matrixLocation[ keycode ] >= 0 )
        {
            int line = matrixLocation[ keycode ] >> 3 ;
            int column = matrixLocation[ keycode ] & 7 ;

            if( keycode == KeyEvent.VK_SHIFT && event.getKeyLocation() == KeyEvent.KEY_LOCATION_RIGHT)
            {
                line = 7 ;
                column = 4;
            }
            keyboardMatrix[ line ] &= ~(1 << column);

            kbdKeyPressed = (keyboardMatrix[ kbdSelectedLine ] & kbdSelectedColumns) != 0 ;
        }
    }

    public void keyTyped( KeyEvent event ){}

//*****************************************************************************************************************
// Class Applet

  public void start()
    {
      Thread thread = new Thread(this);
        thread.start();
    }

//*****************************************************************************************************************
// Class Container

    public Dimension getPreferredSize()
    {
        return new Dimension( 240, 224 );
    }

    public void paint( Graphics gfx )
    {
        gfx.setColor( Color.black );
        gfx.fillRect( 0, 0, getSize().width, getSize().height );
    }

//*****************************************************************************************************************
// Class Component

  //*****************************************************************************************************************
// Interface Runnable

    public void run()
    {
        if( !initialized )
        {
            requestFocus();
            addKeyListener( this );
            keyboardInitialize();

            bufferImage = createImage( new MemoryImageSource( 240, 224, pixelBuffer, 0, 240 ) );

            soundInitialize();

            if( !initialize() ) return;

            initialized = true;
        }

        while( !exit )
        {
            calculateFrame( 1 + frameSkip );

            //System.out.println(this.registerPC);

            Graphics gfx = getGraphics();
            if( gfx != null )
            {
                bufferImage.flush();
                if( scale == 1 ) gfx.drawImage( bufferImage, bufferX, bufferY, this );
                else gfx.drawImage( bufferImage, bufferX, bufferY, 240 * scale, 224 * scale, this );

                if( drawBorders )
                {
                    drawBorders = false;

                    Dimension dim = getSize();
                    int width = dim.width;
                    int height = dim.height;
                    bufferX = ( width - scale * 240 ) / 2;
                    bufferY = ( height - scale * 224 ) / 2;

                    int xEnd = bufferX + scale * 240;
                    int yEnd = bufferY + scale * 224;

                    gfx.setColor( Color.black );
                    if( bufferX > 0 ) gfx.fillRect( 0, 0, bufferX, height );
                    if( bufferY > 0 ) gfx.fillRect( 0, 0, width, bufferY );
                    if( xEnd < width ) gfx.fillRect( xEnd, 0, width - xEnd, height );
                    if( yEnd < height ) gfx.fillRect( 0, yEnd, width, height - yEnd );
                }
            }
        }

        if( frame != null ) frame.dispose();
    }

//*****************************************************************************************************************

    private void errorMessage(String msg)
    {
        System.out.println( msg );
    }


    private final int[][] oricKeyboard = {
        { KeyEvent.VK_3 , KeyEvent.VK_X, KeyEvent.VK_1, 0 , KeyEvent.VK_V, KeyEvent.VK_5, KeyEvent.VK_N, KeyEvent.VK_7 },
        { KeyEvent.VK_D , KeyEvent.VK_Q, KeyEvent.VK_ESCAPE, 0 , KeyEvent.VK_F, KeyEvent.VK_R, KeyEvent.VK_T, KeyEvent.VK_J },
        { KeyEvent.VK_C , KeyEvent.VK_2, KeyEvent.VK_Z, KeyEvent.VK_CONTROL , KeyEvent.VK_4, KeyEvent.VK_B, KeyEvent.VK_6, KeyEvent.VK_M },
        { KeyEvent.VK_QUOTE , KeyEvent.VK_BACK_SLASH, 0, 0 , KeyEvent.VK_MINUS, KeyEvent.VK_SEMICOLON, KeyEvent.VK_9, KeyEvent.VK_K },
        { KeyEvent.VK_RIGHT , KeyEvent.VK_DOWN, KeyEvent.VK_LEFT, KeyEvent.VK_SHIFT , KeyEvent.VK_UP, KeyEvent.VK_PERIOD, KeyEvent.VK_COMMA, KeyEvent.VK_SPACE },
        { KeyEvent.VK_OPEN_BRACKET , KeyEvent.VK_CLOSE_BRACKET, KeyEvent.VK_BACK_SPACE, KeyEvent.VK_ALT, KeyEvent.VK_P, KeyEvent.VK_O, KeyEvent.VK_I, KeyEvent.VK_U },
        { KeyEvent.VK_W , KeyEvent.VK_S, KeyEvent.VK_A, 0 , KeyEvent.VK_E, KeyEvent.VK_G, KeyEvent.VK_H, KeyEvent.VK_Y },
        { KeyEvent.VK_EQUALS , 0, KeyEvent.VK_ENTER, 0 /* RIGHT SHIFT */, KeyEvent.VK_SLASH, KeyEvent.VK_0, KeyEvent.VK_L, KeyEvent.VK_8 }
    };

    private final byte[] matrixLocation = new byte[256];
    private final byte[] keyboardMatrix = new byte[8];

    private void keyboardInitialize()
    {
        Arrays.fill(matrixLocation, (byte) -1);
        for( int line = 0 ; line < 8 ; line ++ )
        {
            for (int column = 0 ; column < 8 ; column ++ )
            {
                int keycode = oricKeyboard[line][7-column] ;
                if( keycode != 0 )
                {
                    matrixLocation[ keycode ] = (byte)(( line << 3 ) + column );
                }
            }
        }
    }



/* memory state */
    private final byte[] rom = new byte[ 16*1024 ];
    private final byte[] ram = new byte[ 48*1024 ];
/* ULA state */
    private boolean graph_mode = false;
    private int frame_count = 0;
    private boolean pal_freq = true;
/* VIA state */
    private int viaDDRB, viaORB, viaPortB;
    private int viaDDRA, viaORA, viaPortA;
    private int viaT1C, viaT1L, viaT2C, viaT2L;
    private boolean viaT1overflow, viaT1running, viaT2overflow, viaT2running;
    private int viaSR, viaACR, viaPCR, viaIER, viaIFR;
    private boolean viaIRQ;
/* PSG state */
    private int psgIndex=15;
    private final byte[] psgRegs = new byte[ 16 ];
    private boolean psgBC1 = false;
    private boolean psgBDIR = false;
    private int psgA_period, psgB_period, psgC_period, psgN_period, psgE_period;
    private int psgA_disabled, psgB_disabled, psgC_disabled, psgNA_disabled, psgNB_disabled, psgNC_disabled;
    private boolean psgIOA_dir;
    private boolean psgA_envelop, psgB_envelop, psgC_envelop;
    private int psgA_amplitude, psgB_amplitude, psgC_amplitude, psgE_amplitude;
    private boolean psgE_hold, psgE_alternate, psgE_attack, psgE_continue, psgE_countup, psgE_countdown;
    private int psgA_counter, psgB_counter, psgC_counter, psgN_counter, psgE_counter;
    private int psgA_output, psgB_output, psgC_output, psgN_output;
    private int psgN_shifter;
    private final byte[] psgA_chanel = new byte[1248];   // need 1248 values per Frame
    private final byte[] psgB_chanel = new byte[1248];   // need 1248 values per Frame
    private final byte[] psgC_chanel = new byte[1248];   // need 1248 values per Frame
    private int psgOutIndex = 0;
/* audio state */
    private final float audioFreq = (float)22050;
    private final byte[] volume = { 0, 2, 3, 4, 6, 8, 11, 16, 23, 32, 45, 64, 90,(byte)128,(byte)181,(byte)255 };
    private final byte[] audioBufA = new byte[440];  // need 440 bytes per Frame at 22050 Hz
    private final byte[] audioBufB = new byte[440];  // need 440 bytes per Frame at 22050 Hz
    private final byte[] audioBufC = new byte[440];  // need 440 bytes per Frame at 22050 Hz
/* keyboard state */
    private int kbdSelectedColumns = 0;
    private int kbdSelectedLine = 0;
    private boolean kbdKeyPressed;
/* tape emulation */
    private boolean tapeBoot = false;
  private int tapeFileNameAddr = 0x27F;
    private int tapeNameFoundAddr = 0x293;
    private int tapeHeaderAddr = 0x2A7;
    private InputStream tapeStream;

/* CPU state */
    private boolean nmi = false;
    private boolean reset = false;
    private int registerPC;
    private byte registerA=0,registerX=0,registerY=0;
    private byte registerS = (byte)0xFF;
    private byte registerP;

    private InputStream openFile( String name )
    {
        InputStream stream;

        try
        {
            if( frame != null )
            {
                stream = new FileInputStream( name );
            }
            else
            {
                stream = new URL( name ).openStream();
            }

            return new BufferedInputStream( stream );
        }
        catch( Exception e)
        {
            errorMessage("Couldn't open "+ name);
            return null;
        }
    }


    private void loadROM(String name, byte[] dest)
    throws Exception
    {
        InputStream stream = openFile( name );

        int bytesRead = 0;
        while( bytesRead < dest.length )
        {
            if( frame == null ) System.out.println("Loading ROM : "+bytesRead+" bytes");

            bytesRead += stream.read( dest, bytesRead, dest.length-bytesRead );
        }
        stream.close();
    }

    private boolean initialize()
    {
        try
        {
            loadROM( "BASIC11B.ROM", rom );
        }
        catch( Exception e )
        {
            return false;
        }

        if( TAPE_NAME != null )
        {
            System.out.println(appletName);
            try
            {
                String filename = TAPE_NAME;
                if (filename!=null)
                {
                    tapeBoot = true;
                    tapeStream = openFile( filename );
                }
            }
            catch (Exception e)
            {
            }
        }

        if( deek(0xFFFC) == 0xF88F )
        {
            if (tapeBoot) rom[ 0x0592 ] = 2;
            tapeFileNameAddr = 0x27F;
            tapeNameFoundAddr = 0x293;
            tapeHeaderAddr = 0x2A7;
            rom[0x2735] = rom[0x26C9] = 2;

            if( frame != null )
                rom[0x275E] = rom[0x265E] = 2;
        }

        if( deek(0xFFFC) == 0xF42D )
        {
            if (tapeBoot) rom[ 0x05A2 ] = 2;
            tapeFileNameAddr = 0x35;
            tapeNameFoundAddr = 0x49;
            tapeHeaderAddr = 0x5D;
            rom[0x2696] = rom[0x2630] = 2;

            if( frame != null )
                rom[0x26BE] = rom[0x25C6] = 2;
        }

        for( int i=0; i<ram.length; i++ )
        {
            if( ( i & 0x80 ) != 0 ) ram[ i ] = (byte)0xFF;
            else ram[ i ] = 0x00;
        }

        viaORB = viaDDRB = 0;
        viaORA = viaDDRA = 0;
        viaACR = viaPCR = viaSR = 0;
        viaIFR = viaIER = 0;
        viaT1L = viaT1C = viaT2L = viaT2C = 0;
        viaT1overflow = viaT1running = false;
        viaT2overflow = viaT2running = false;
        viaIRQ = false;

        psgA_output = psgB_output = psgC_output = psgN_output = 0;
        psgA_period = psgB_period = psgC_period = psgN_period = 0;
        psgA_disabled = psgB_disabled = psgC_disabled = 0;
        psgNA_disabled = psgNB_disabled = psgNC_disabled = 0;
        psgN_shifter = 1;

        registerPC = deek(0xFFFC);
        registerP = 0x20 ;

        return true;
    }

    private void emulatorTrap(int pc)
    {
        switch( pc )
        {
            case 0xC592:
            case 0xC5A2:
                tapeEnterCommand();
                break;

            case 0xE735:
            case 0xE696:
                tapeReadSynchro();
                break;

            case 0xE6C9:
            case 0xE630:
                tapeReadByte();
                break;

/*
            case 0xE75E:
            case 0xE6BE:
                tapeWriteSynchro();
                break;

            case 0xE65E:
            case 0xE5C6:
                tapeWriteByte();
                break;
*/
        }
    }

    private void tapeReadByte()
    {
        if( deek(0xFFFC) == 0xF88F )
            registerPC = 0xE6FB;
        else
            registerPC = 0xE65D;

        try
        {
            if( tapeStream != null )
                registerA = (byte)tapeStream.read();
        }
        catch( Exception e )
        {
        }

        registerP &= ~3; // Z=C=0
        if( registerA == 0 ) registerP |= 2;
    }

    private void tapeReadSynchro()
    {
        boolean synchroFound = false;
        boolean alreadyOpenedOnce = false;

        if( deek(0xFFFC) == 0xF88F )
            registerPC = 0xE6FB;
        else
            registerPC = 0xE65D;

        registerX = 0;
        registerP |= 2 ;   // Z=1

      boolean tapeLoading = true;
      if( !tapeLoading && tapeStream != null )
        {
            try
            {
                tapeStream.close();
            }
            catch( Exception e )
            {
            }
            tapeStream = null;
        }

        while( !synchroFound )
        {
            if( tapeStream != null && tapeLoading)
            {
                try
                {
                    int val = tapeStream.read();

                    if( val == 0x16 ) synchroFound = true;

                    if( val == -1 )
                    {
                        tapeStream.close();
                        tapeStream = null;
                    }
                }
                catch( Exception e )
                {
                }
            }
            else
            {
                if( alreadyOpenedOnce ) return;

                int nameLength = 0;
                while( ram[ tapeFileNameAddr + nameLength ] != 0 )
                    nameLength ++;

                String name = new String( ram, tapeFileNameAddr, nameLength );
                tapeStream = openFile( name );
                if( tapeStream == null ) return;

                alreadyOpenedOnce = true;
                ram[ tapeFileNameAddr ] = 0;
            }
        }
    }

    private void tapeEnterCommand()
    {
        rom[ registerPC - 48*1024 ] = (byte)0xA2; // restore LDX opcode
        if( deek(0xFFFC) == 0xF88F )
            registerPC = 0xE6FB;
        else
            registerPC = 0xE65D;

        registerX = 0x34;
        registerY = 0;
        ram[0xBC9A] = ram[0x35] = 0x43; // 'C'
        ram[0xBC9B] = ram[0x36] = 0x4C; // 'L'
        ram[0xBC9C] = ram[0x37] = 0x4F; // 'O'
        ram[0xBC9D] = ram[0x38] = 0x41; // 'A'
        ram[0xBC9E] = ram[0x39] = 0x44; // 'D'
        ram[0xBC9F] = ram[0x3A] = 0x22; // '"'
        ram[0x3B] = 0;
    }

    private void soundInitialize()
    {
        if( EMULATE_SOUND )
        {
            try
            {
                int bufferSize = 4096;
                AudioFormat format = new AudioFormat(
                                        audioFreq,
                                        8, // 8 bits samples
                                        1, // mono
                                        false, // unsigned
                                        false // little-endian byte order : useless
                                        );
                DataLine.Info info = new DataLine.Info( SourceDataLine.class, format, bufferSize );

                waveOutA = (SourceDataLine)AudioSystem.getLine( info );
                waveOutA.open( format );

                waveOutB = (SourceDataLine)AudioSystem.getLine( info );
                waveOutB.open( format );

                waveOutC = (SourceDataLine)AudioSystem.getLine( info );
                waveOutC.open( format );
            }
            catch( LineUnavailableException e)
            {
                errorMessage("Unable to get an audio line...");
                return;
            }

            for(int i=0 ; i < audioBufA.length ; i++ )
            {
                audioBufA[ i ] = 0;
                audioBufB[ i ] = 0;
                audioBufC[ i ] = 0;
            }

            waveOutA.write( audioBufA, 0 , audioBufA.length );
            waveOutB.write( audioBufB, 0 , audioBufB.length );
            waveOutC.write( audioBufC, 0 , audioBufC.length );

            waveOutA.start();
            waveOutB.start();
            waveOutC.start();
        }
    }


    private void calculateFrame( int nbFrames )
    {
        int psgCounter=16;
        int remaining_cycles=0;
        byte[] ram = this.ram ;
        int pc = registerPC ;
        int address = 0 ;
        byte tmp = 0, tmp2 = 0;
        byte a = registerA;
        byte x = registerX;
        byte y = registerY;
        byte s = registerS;
        boolean flagN = registerP < 0;
        boolean flagZ = ( registerP & 2 ) != 0;
        boolean flagV = ( registerP & 64 ) != 0;
        boolean flagD = ( registerP & 8 ) != 0;
        boolean flagI = ( registerP & 4 ) != 0;
        boolean flagC = ( registerP & 1 ) != 0;
        int k;

        while( nbFrames != 0 )
        {
            nbFrames --;
            if( EMULATE_SOUND ) writeAudio();
            else timeSynchro();
            psgOutIndex = 0;

            int column = 0;
            int dot_ink, dot_paper, pattern;
            int[] screen = pixelBuffer;
            int screenIndex = 0;
            int line = 0 , charline = 0;
            int ink = palette[7] , paper = palette[0];
            int total_lines = pal_freq ? 312 : 264;
            boolean blink = false , dbl_height = false;
            int blink_mask = 0x3F ;
            int charset_base = graph_mode ? 0x9800 : 0xB400;
            int charset = 0 , charset_addr = charset_base;
            boolean char_display = !graph_mode;
            int line_addr = char_display ? 0xBB80 : 0xA000;
            frame_count ++ ;
            boolean blank_lines = false;
            boolean frameDone = false;

            while( !frameDone )
            {

                if( nbFrames == 0 && !blank_lines && column < 40 )
                {

                    int videobyte = ram[ line_addr + column ];
                    pattern = videobyte;

                    if( char_display )
                    {
                        pattern = ram[ charset_addr + ((videobyte & 0x7F) << 3) + charline];
                    }

                    if( ( videobyte & 0x60 ) == 0 )
                    {
                        pattern = 0;
                        switch (videobyte & 0x18)
                        {

                            case 0:
                                ink = palette[ videobyte & 7 ];
                                break;

                            case 8:
                                charset = videobyte & 1;
                                charset_addr = charset_base + (charset<<10);
                                dbl_height = ( videobyte & 2 ) != 0;
                                charline = dbl_height ? (line & 15) >> 1 : line & 7;
                                blink = ( videobyte & 4 ) != 0;
                                blink_mask = ( blink && (frame_count&0x10) != 0 ) ? 0 : 0x3F;
                                break;

                            case 0x10:
                                paper = palette[ videobyte & 7 ];
                                break;

                            case 0x18:
                                pal_freq = ( videobyte & 2 ) != 0 ;
                                total_lines = pal_freq ? 312 : 264;
                                graph_mode = ( videobyte & 4 ) != 0;
                                charset_base = graph_mode ? 0x9800 : 0xB400;
                                charset_addr = charset_base + (charset << 10);
                                char_display = !graph_mode || line >= 200;
                                if (char_display)
                                    line_addr = 0xBB80 + (line>>3)*40;
                                else
                                    line_addr = 0xA000 + line*40;
                                break;
                        }
                    }
                    else
                    {
                        pattern &= blink_mask;
                    }

                    if( videobyte < 0 ) {
                        dot_ink = ink ^ 0x00FFFFFF;
                        dot_paper = paper ^ 0x00FFFFFF;
                    } else {
                        dot_ink = ink ;
                        dot_paper = paper;
                    }
                    screen[ screenIndex++ ] = ( pattern & 0x20 ) != 0 ? dot_ink : dot_paper ;
                    screen[ screenIndex++ ] = ( pattern & 0x10 ) != 0 ? dot_ink : dot_paper ;
                    screen[ screenIndex++ ] = ( pattern & 0x08 ) != 0 ? dot_ink : dot_paper ;
                    screen[ screenIndex++ ] = ( pattern & 0x04 ) != 0 ? dot_ink : dot_paper ;
                    screen[ screenIndex++ ] = ( pattern & 0x02 ) != 0 ? dot_ink : dot_paper ;
                    screen[ screenIndex++ ] = ( pattern & 0x01 ) != 0 ? dot_ink : dot_paper ;

                }

                column ++;

                if( column == 64 )
                {
                    column = 0;
                    line ++ ;

                    if( line < 224 ) {

                        charline = line & 7;
                        column = 0 ; ink = palette[7] ; paper = palette[0];
                        blink = false ; blink_mask = 0x3F ; dbl_height = false;
                        charset = 0 ; charset_addr = charset_base;

                        if( line == 200 ) char_display = true;

                        if( char_display ) line_addr = 0xBB80 + (line >> 3)*40;
                        else line_addr = 0xA000 + line*40;

                    }
                    else if( line == total_lines )
                    {
                        frameDone = true;
                    }
                    else
                    {
                        blank_lines = true;
                    }
                }

                psgCounter --;
                if( psgCounter == 0 )
                {
                    psgCounter = 16;
                    updatePSG();
                }

                if( remaining_cycles > 0 )
                {
                    remaining_cycles--;
                }
                else
                {
                    if( viaIRQ )
                    {
                        if( !flagI )
                        {
                            tmp = 0x30;
                            if( flagN ) tmp += 128;
                            if( flagV ) tmp += 64;
                            if( flagD ) tmp += 8;
                            if( flagI ) tmp += 4;
                            if( flagZ ) tmp += 2;
                            if( flagC ) tmp += 1;

                            ram[ 0x100 + ( 0xFF & (int)s-- ) ] = (byte)( pc >> 8 );
                            ram[ 0x100 + ( 0xFF & (int)s-- ) ] = (byte)pc ;
                            ram[ 0x100 + ( 0xFF & (int)s-- ) ] = (byte)( tmp & ~0x10 );
                            pc = deek( 0xFFFE );
                            flagI = true;
                        }
                    }

                    if( nmi )
                    {
                        nmi = false;

                        if( reset )
                        {
                            reset = false;
                            boolean dummy = initialize();
                            pc = registerPC;
                            flagN = registerP < 0;
                            flagZ = ( registerP & 2 ) != 0;
                            flagV = ( registerP & 64 ) != 0;
                            flagD = ( registerP & 8 ) != 0;
                            flagI = ( registerP & 4 ) != 0;
                            flagC = ( registerP & 1 ) != 0;
                        }
                        else
                        {
                            tmp = 0x30;
                            if( flagN ) tmp += 128;
                            if( flagV ) tmp += 64;
                            if( flagD ) tmp += 8;
                            if( flagI ) tmp += 4;
                            if( flagZ ) tmp += 2;
                            if( flagC ) tmp += 1;

                            ram[ 0x100 + ( 0xFF & (int)s-- ) ] = (byte)( pc >> 8 );
                            ram[ 0x100 + ( 0xFF & (int)s-- ) ] = (byte)pc ;
                            ram[ 0x100 + ( 0xFF & (int)s-- ) ] = tmp ;
                            pc = deek( 0xFFFA );
                        }
                    }

                    int zp_address;
                    int opcode = peek( pc++ );

                    if( ( opcode & 0xC0 ) == 0x80 )
                    {
                        remaining_cycles = 1;

                        switch( opcode & 0xFF )
                        {
                            case 0x81: // STA Indexed X Indirect
                                zp_address = 0xFF & ( peek( pc++ ) + x ) ;
                                poke( ram[ zp_address ] & 0xFF | ( ram[ zp_address + 1 ] << 8 ) & 0xFF00 , a );
                                remaining_cycles = 5 ;
                                break;

                            case 0x84: // STY Zero page
                                ram[ peek( pc++ ) & 0xFF ] = y;
                                remaining_cycles = 2 ;
                                break;

                            case 0x85: // STA Zero page
                                ram[ peek( pc++ ) & 0xFF ] = a;
                                remaining_cycles = 2 ;
                                break;

                            case 0x86: // STX Zero page
                                ram[ peek( pc++ ) & 0xFF ] = x;
                                remaining_cycles = 2 ;
                                break;

                            case 0x88: // DEY
                                y--;
                                flagN = y < 0;
                                flagZ = y == 0;
                                break;

                            case 0x8A: // TXA
                                a = x;
                                flagN = a < 0;
                                flagZ = a == 0;
                                break;

                            case 0x8C: // STY Absolute
                                poke( peek( pc++ ) & 0xFF | ( peek( pc++ ) << 8 ) & 0xFF00 , y );
                                remaining_cycles = 3;
                                break;

                            case 0x8D: // STA Absolute
                                poke( peek( pc++ ) & 0xFF | ( peek( pc++ ) << 8 ) & 0xFF00 , a );
                                remaining_cycles = 3;
                                break;

                            case 0x8E: // STX Absolute
                                poke( peek( pc++ ) & 0xFF | ( peek( pc++ ) << 8 ) & 0xFF00 , x );
                                remaining_cycles = 3;
                                break;

                            case 0x90: // BCC
                                if( !flagC )
                                {
                                    pc += peek( pc );
                                    remaining_cycles = 2;
                                }
                                pc++;
                                break;

                            case 0x91: // STA (),Y
                                zp_address = 0xFF & peek( pc++ ) ;
                                poke( ( ram[ zp_address ] & 0xFF | ( ram[ zp_address + 1 ] << 8 ) & 0xFF00 ) + ( 0xFF & y ) , a );
                                remaining_cycles = 5;
                                break;

                            case 0x94: // STY Page Zero Indexed X
                                ram[ 0xFF & ( peek( pc++ ) + x ) ] = y ;
                                remaining_cycles = 3;
                                break;

                            case 0x95: // STA Page Zero Indexed X
                                ram[ 0xFF & ( peek( pc++ ) + x ) ] = a ;
                                remaining_cycles = 3;
                                break;

                            case 0x96: // STX Page Zero Indexed Y
                                ram[ 0xFF & ( peek( pc++ ) + y ) ] = x ;
                                remaining_cycles = 3;
                                break;

                            case 0x98: // TYA
                                a = y;
                                flagN = a < 0;
                                flagZ = a == 0;
                                break;

                            case 0x99: // STA Absolute Indexed Y
                                poke ( ( peek( pc++ ) & 0xFF ) + ( ( peek( pc++ ) & 0xFF ) << 8 ) + ( y & 0xFF ) , a );
                                remaining_cycles = 4 ;
                                break;

                            case 0x9A: // TXS
                                s = x;
                                break;

                            case 0x9D: // STA Absolute Indexed X
                                poke ( ( peek( pc++ ) & 0xFF ) + ( ( peek( pc++ ) & 0xFF ) << 8 ) + ( x & 0xFF ) , a );
                                remaining_cycles = 4 ;
                                break;

                            case 0xA0:  // LDY Immediate
                                y = peek( pc++ );
                                flagN = y < 0;
                                flagZ = y == 0;
                                break;

                            case 0xA1: // LDA Indexed X Indirect
                                address = 0xFF & ( peek( pc++ ) + x ) ;
                                a = peek( ram[ address ] & 0xFF | ( ram[ address + 1 ] << 8 ) & 0xFF00 ) ;
                                flagN = a < 0;
                                flagZ = a == 0;
                                remaining_cycles = 5;
                                break;

                            case 0xA2:  // LDX Immediate
                                x = peek( pc++ );
                                flagN = x < 0;
                                flagZ = x == 0;
                                break;

                            case 0xA4:  // LDY Zero Page
                                y = ram[ 0xFF & peek( pc++ ) ];
                                flagN = y < 0;
                                flagZ = y == 0;
                                remaining_cycles = 2;
                                break;

                            case 0xA5: // LDA Zero page
                                a = ram[ 0xFF & peek( pc++ ) ];
                                flagN = a < 0;
                                flagZ = a == 0;
                                remaining_cycles = 2;
                                break;

                            case 0xA6: // LDX Zero page
                                x = ram[ 0xFF & peek( pc++ ) ];
                                flagN = x < 0;
                                flagZ = x == 0;
                                remaining_cycles = 2;
                                break;

                            case 0xA8:  // TAY
                                y = a;
                                flagN = y < 0;
                                flagZ = y == 0;
                                break;

                            case 0xA9: // LDA Immediate
                                a = peek( pc++ );
                                flagN = a < 0;
                                flagZ = a == 0;
                                break;

                            case 0xAA:  // TAX
                                x = a;
                                flagN = x < 0;
                                flagZ = x == 0;
                                break;

                            case 0xAC:  // LDY Absolute
                                y = peek( peek( pc++ ) & 0xFF | ( peek( pc++ ) << 8 ) & 0xFF00 );
                                flagN = y < 0;
                                flagZ = y == 0;
                                remaining_cycles = 3;
                                break;

                            case 0xAD: // LDA Absolute
                                a = peek( peek( pc++ ) & 0xFF | ( peek( pc++ ) << 8 ) & 0xFF00 );
                                flagN = a < 0;
                                flagZ = a == 0;
                                remaining_cycles = 3;
                                break;

                            case 0xAE: // STX Absolute
                                x = peek( peek( pc++ ) & 0xFF | ( peek( pc++ ) << 8 ) & 0xFF00 );
                                flagN = x < 0;
                                flagZ = x == 0;
                                remaining_cycles = 3;
                                break;

                            case 0xB0: // BCS
                                if( flagC )
                                {
                                    pc += peek( pc );
                                    remaining_cycles = 2;
                                }
                                pc++;
                                break;

                            case 0xB1: // LDA (),Y
                                zp_address = 0xFF & peek( pc++ ) ;
                                address = ( ram[ zp_address ] & 0xFF ) + ( y & 0xFF ) ;
                                remaining_cycles = address > 255 ? 5 : 4;
                                address += ( ram[ zp_address + 1 ] << 8 ) & 0xFF00 ;
                                a = peek( address );
                                flagN = a < 0;
                                flagZ = a == 0;
                                break;

                            case 0xB4: // LDY Page Zero Indexed X
                                y = ram[ 0xFF & ( peek( pc++ ) + x ) ];
                                flagN = y < 0;
                                flagZ = y == 0;
                                remaining_cycles = 3;
                                break;

                            case 0xB5: // LDA Page Zero Indexed X
                                a = ram[ 0xFF & ( peek( pc++ ) + x ) ];
                                flagN = a < 0;
                                flagZ = a == 0;
                                remaining_cycles = 3;
                                break;

                            case 0xB6: // LDX Page Zero Indexed Y
                                x = ram[ 0xFF & ( peek( pc++ ) + y ) ];
                                flagN = x < 0;
                                flagZ = x == 0;
                                remaining_cycles = 3;
                                break;

                            case 0xB8: // CLV
                                flagV = false;
                                break;

                            case 0xB9: // LDA Absolute Indexed Y
                                address = ( peek( pc++ ) & 0xFF ) + ( y & 0xFF );
                                remaining_cycles = address > 255 ? 4 : 3 ;
                                address = ( ( peek( pc++ ) << 8 ) + address ) & 0xFFFF ;
                                a = peek( address );
                                flagN = a < 0;
                                flagZ = a == 0;
                                break;

                            case 0xBA: // TSX
                                x = s;
                                flagN = x < 0;
                                flagZ = x == 0;
                                break;

                            case 0xBC:  // LDY Absolute Indexed X
                                address = ( peek( pc++ ) & 0xFF ) + ( x & 0xFF );
                                remaining_cycles = address > 255 ? 4 : 3 ;
                                address = ( ( peek( pc++ ) << 8 ) + address ) & 0xFFFF ;
                                y = peek( address );
                                flagN = y < 0;
                                flagZ = y == 0;
                                break;

                            case 0xBD: // LDA Absolute Indexed X
                                address = ( peek( pc++ ) & 0xFF ) + ( x & 0xFF );
                                remaining_cycles = address > 255 ? 4 : 3 ;
                                address = ( ( peek( pc++ ) << 8 ) + address ) & 0xFFFF ;
                                a = peek( address );
                                flagN = a < 0;
                                flagZ = a == 0;
                                break;

                            case 0xBE: // LDX Absolute Indexed Y
                                address = ( peek( pc++ ) & 0xFF ) + ( y & 0xFF );
                                remaining_cycles = address > 255 ? 4 : 3 ;
                                address = ( ( peek( pc++ ) << 8 ) + address ) & 0xFFFF ;
                                x = peek( address );
                                flagN = x < 0;
                                flagZ = x == 0;
                                break;
                        }
                    }
                    else // (opcode & 0xC0) != 0x80
                    {
                        boolean rmw = false;
                        remaining_cycles = 0;

                        switch( opcode & 0x1F ) // addressing mode
                        {
                            case 0x01: // Indexed X Indirect
                                zp_address = 0xFF & ( peek( pc++ ) + x ) ;
                                address = ram[ zp_address ] & 0xFF | ( ram[ zp_address + 1 ] << 8 ) & 0xFF00 ;
                                tmp = peek( address ) ;
                                remaining_cycles = 5 ;
                                break;

                            case 0x05: // Zero page
                                tmp = ram[ peek( pc++ ) & 0xFF ];
                                remaining_cycles = 2 ;
                                break;

                            case 0x06: // RMW Zero page
                                address = peek( pc++ ) & 0xFF ;
                                tmp = ram[ address ];
                                remaining_cycles = 4 ;
                                rmw = true;
                                break;

                            case 0x09: // Immediate
                                tmp = peek( pc++ );
                                remaining_cycles = 1 ;
                                break;

                            case 0x0D: // Absolute
                                address = deek( pc );
                                pc+=2;
                                tmp = peek( address );
                                remaining_cycles = 3;
                                break;

                            case 0x0E: // RMW Absolute
                                address = deek( pc );
                                pc+=2;
                                tmp = peek( address );
                                remaining_cycles = 5;
                                rmw = true;
                                break;

                            case 0x11: // Indirect Indexed Y
                                zp_address = 0xFF & peek( pc++ ) ;
                                address = ( ram[ zp_address ] & 0xFF | ( ram[ zp_address + 1 ] << 8 ) & 0xFF00 ) + ( 0xFF & y ) ;
                                tmp = peek( address );
                                remaining_cycles = 4; // +1 if addition of Y changes page
                                break;

                            case 0x15: // Page Zero Indexed X
                                tmp = ram[ 0xFF & ( peek( pc++ ) + x ) ];
                                remaining_cycles = 3;
                                break;

                            case 0x16: // RMW Page Zero Indexed X
                                address = ( peek( pc++ ) + x ) & 0xFF ;
                                tmp = ram[ address ];
                                remaining_cycles = 5;
                                rmw = true;
                                break;

                            case 0x19: // Absolute Indexed Y
                                address = ( peek( pc++ ) & 0xFF ) + ( y & 0xFF );
                                remaining_cycles = address > 255 ? 4 : 3 ;
                                address = ( ( peek( pc++ ) << 8 ) + address ) & 0xFFFF ;
                                tmp = peek( address );
                                break;

                            case 0x1E: // RMW Absolute Indexed X
                                rmw = true;
                            case 0x1D: // Absolute Indexed X
                                address = ( peek( pc++ ) & 0xFF ) + ( x & 0xFF );
                                remaining_cycles = address > 255 ? 4 : 3 ;
                                address = ( ( peek( pc++ ) << 8 ) + address ) & 0xFFFF ;
                                tmp = peek( address );
                                break;
                        }

                        if( remaining_cycles == 0) // not a regular instruction with addressing mode
                        {
                            remaining_cycles = 1;

                            switch( opcode & 0xFF )
                            {
                            case 0x00:  // BRK
                                tmp = 0x30;
                                if( flagN ) tmp += 128;
                                if( flagV ) tmp += 64;
                                if( flagD ) tmp += 8;
                                if( flagI ) tmp += 4;
                                if( flagZ ) tmp += 2;
                                if( flagC ) tmp += 1;

                                ram[ 0x100 + ( 0xFF & (int)s-- ) ] = (byte)( ( pc + 1 ) >> 8 ) ;
                                ram[ 0x100 + ( 0xFF & (int)s-- ) ] = (byte)( pc + 1 ) ;
                                ram[ 0x100 + ( 0xFF & (int)s-- ) ] = tmp ;
                                flagI = true;
                                pc = deek( 0xFFFE );
                                remaining_cycles = 6;
                                break;

                            case 0x20:  // JSR
                                pc ++;
                                ram[ 0x100 + ( 0xFF & (int)s-- ) ] = (byte)( pc >> 8 ) ;
                                ram[ 0x100 + ( 0xFF & (int)s-- ) ] = (byte)pc ;
                                pc = deek( pc - 1 );
                                break;

                            case 0x40:  // RTI
                                tmp = ram[ 0x100 + ( 0xFF & (int)(++s) ) ] ;
                                flagN = tmp < 0;
                                flagV = ( tmp & 64 ) != 0;
                                flagD = ( tmp & 8 ) != 0;
                                flagI = ( tmp & 4 ) != 0;
                                flagZ = ( tmp & 2 ) != 0;
                                flagC = ( tmp & 1 ) != 0;
                                pc = 0xFF & (int)ram[ 0x100 + ( 0xFF & (int)(++s) ) ];
                                pc += 0xFF00 & ( ram[ 0x100 + ( 0xFF & (int)(++s) ) ] << 8 );
                                remaining_cycles = 5;
                                break;

                            case 0x60:  // RTS
                                pc = 0xFF & ram[ 0x100 + ( 0xFF & (int)(++s) ) ];
                                pc += 0xFF00 & ( ram[ 0x100 + ( 0xFF & (int)(++s) ) ] << 8 );
                                pc++;
                                remaining_cycles = 5;
                                break;

                            case 0xC0:  // CPY Immediate
                                tmp = peek( pc++ );
                                flagC = ( 0xFF & (int)tmp ) <= ( 0xFF & (int)y );
                                flagN = (byte)( y - tmp ) < 0 ;
                                flagZ = y == tmp ;
                                break;

                            case 0xE0:  // CPX Immediate
                                tmp = peek( pc++ );
                                flagN = (byte)( x - tmp ) < 0 ;
                                flagZ = x == tmp ;
                                flagC = ( 0xFF & (int)tmp ) <= ( 0xFF & (int)x );
                                break;

                            case 0x24:  // BIT Zero Page
                                tmp = ram[ 0xFF & peek( pc++ ) ];
                                flagZ = ( a & tmp ) == 0 ;
                                flagN = ( tmp & 0x80 ) != 0;
                                flagV = ( tmp & 0x40 ) != 0;
                                remaining_cycles = 2;
                                break;

                            case 0xC4:  // CPY Zero Page
                                tmp = ram[ 0xFF & peek( pc++ ) ];
                                flagC = ( 0xFF & (int)tmp ) <= ( 0xFF & (int)y );
                                flagN = (byte)( y - tmp ) < 0 ;
                                flagZ = y == tmp ;
                                remaining_cycles = 2;
                                break;

                            case 0xE4:  // CPX Zero Page
                                tmp = ram[ 0xFF & peek( pc++ ) ];
                                flagC = ( 0xFF & (int)tmp ) <= ( 0xFF & (int)x );
                                flagN = (byte)( x - tmp ) < 0 ;
                                flagZ = x == tmp ;
                                remaining_cycles = 2;
                                break;

                            case 0x08:  // PHP
                                tmp = 0x30;
                                if( flagN ) tmp += 128;
                                if( flagV ) tmp += 64;
                                if( flagD ) tmp += 8;
                                if( flagI ) tmp += 4;
                                if( flagZ ) tmp += 2;
                                if( flagC ) tmp += 1;
                                ram[ 0x100 + ( 0xFF & (int)s-- ) ] = tmp ;
                                remaining_cycles = 2;
                                break;

                            case 0x28:  // PLP
                                tmp = ram[ 0x100 + ( 0xFF & (int)(++s) ) ] ;
                                flagN = tmp < 0;
                                flagV = ( tmp & 64 ) != 0;
                                flagD = ( tmp & 8 ) != 0;
                                flagI = ( tmp & 4 ) != 0;
                                flagZ = ( tmp & 2 ) != 0;
                                flagC = ( tmp & 1 ) != 0;
                                remaining_cycles = 3;
                                break;

                            case 0x48:  // PHA
                                ram[ 0x100 + ( 0xFF & (int)s-- ) ] = a ;
                                remaining_cycles = 2;
                                break;

                            case 0x68:  // PLA
                                a = ram[ 0x100 + ( 0xFF & (int)(++s) ) ];
                                flagN = a < 0;
                                flagZ = a == 0;
                                remaining_cycles = 3;
                                break;

                            case 0xC8:  // INY
                                y++;
                                flagN = y < 0;
                                flagZ = y == 0;
                                break;

                            case 0xE8:  // INX
                                x++;
                                flagN = x < 0;
                                flagZ = x == 0;
                                break;

                            case 0x0A:  // ASL A
                                flagC = a < 0;
                                a <<= 1;
                                flagN = a < 0;
                                flagZ = a == 0;
                                break;

                            case 0x2A:  // ROL A
                                tmp = (byte)( a << 1 );
                                if( flagC ) tmp |= 1;
                                flagC = a < 0;
                                flagN = tmp < 0;
                                flagZ = tmp == 0;
                                a = tmp;
                                break;

                            case 0x4A:  // LSR A
                                flagC = ( a & 1 ) != 0;
                                a = (byte)( ( a & 0xFF ) >> 1 );
                                flagN = false;
                                flagZ = a == 0;
                                break;

                            case 0x6A:  // ROR A
                                tmp = (byte)( ( a & 0xFF ) >> 1 );
                                if( flagC ) tmp |= 0x80;
                                flagC = ( a & 1 ) != 0;
                                flagN = tmp < 0;
                                flagZ = tmp == 0;
                                a = tmp;
                                break;

                            case 0xCA:  // DEX
                                x--;
                                flagN = x < 0;
                                flagZ = x == 0;
                                break;

                            case 0xEA:  // NOP
                                break;

                            case 0x2C:  // BIT Absolute
                                tmp = peek( peek( pc++ ) & 0xFF | ( peek( pc++ ) << 8 ) & 0xFF00 );
                                flagZ = ( a & tmp ) == 0 ;
                                flagN = ( tmp & 0x80 ) != 0;
                                flagV = ( tmp & 0x40 ) != 0;
                                remaining_cycles = 3;
                                break;

                            case 0x4C:  // JMP Absolute
                                pc = deek( pc );
                                remaining_cycles = 2;
                                break;

                            case 0x6C:  // JMP Absolute Indirect
                                byte address_low = peek( pc );
                                address = ( peek( pc + 1 ) << 8 ) & 0xFF00;
                                pc = peek( address + ( address_low & 0xFF ) ) & 0xFF ;
                                pc += ( peek( address + (( address_low + 1 ) & 0xFF) ) << 8 ) & 0xFF00 ;
                                remaining_cycles = 4;
                                break;

                            case 0xCC:  // CPY Absolute
                                address = peek( pc++ ) & 0xFF | ( peek( pc++ ) << 8 ) & 0xFF00;
                                tmp = peek( address );
                                flagC = ( 0xFF & (int)tmp ) <= ( 0xFF & (int)y );
                                flagN = (byte)( y - tmp ) < 0 ;
                                flagZ = y == tmp ;
                                remaining_cycles = 3;
                                break;

                            case 0xEC:  // CPX Absolute
                                address = peek( pc++ ) & 0xFF | ( peek( pc++ ) << 8 ) & 0xFF00;
                                tmp = peek( address );
                                flagN = (byte)( x - tmp ) < 0 ;
                                flagZ = x == tmp ;
                                flagC = ( 0xFF & (int)tmp ) <= ( 0xFF & (int)x );
                                remaining_cycles = 3;
                                break;

                            case 0x10:  // BPL
                                if( !flagN )
                                {
                                    remaining_cycles = 2;
                                    pc += peek( pc );
                                }
                                pc++;
                                break;

                            case 0x30:  // BMI
                                if( flagN )
                                {
                                    pc += peek( pc );
                                    remaining_cycles = 2;
                                }
                                pc++;
                                break;

                            case 0x50:  // BVC
                                if( !flagV )
                                {
                                    pc += peek( pc );
                                    remaining_cycles = 2;
                                }
                                pc++;
                                break;

                            case 0x70:  // BVS
                                if( flagV )
                                {
                                    pc += peek( pc );
                                    remaining_cycles = 2;
                                }
                                pc++;
                                break;

                            case 0xD0:  // BNE
                                if( !flagZ )
                                {
                                    pc += peek( pc );
                                    remaining_cycles = 2;
                                }
                                pc++;
                                break;

                            case 0xF0:  // BEQ
                                if( flagZ )
                                {
                                    pc += peek( pc );
                                    remaining_cycles = 2;
                                }
                                pc++;
                                break;

                            case 0x18:  // CLC
                                flagC = false;
                                break;

                            case 0x38:  // SEC
                                flagC = true;
                                break;

                            case 0x58:  // CLI
                                flagI = false;
                                break;

                            case 0x78:  // SEI
                                flagI = true;
                                break;

                            case 0xD8:  // CLD
                                flagD = false;
                                break;

                            case 0xF8:  // SED
                                flagD = true;
                                break;

                            case 0x02: // Illegal opcodes, should hang but used as Emulator Trap
                                pc --;
                                registerPC = pc;
                                this.registerA = a;
                                this.registerX = x;
                                this.registerY = y;
                                this.registerP = 0x30;
                                if( flagI ) this.registerP |= 4;
                                emulatorTrap(pc);
                                a = this.registerA;
                                x = this.registerX;
                                y = this.registerY;
                                pc = registerPC;
                                flagN = registerP < 0;
                                flagV = ( registerP & 64 ) != 0;
                                flagD = ( registerP & 8 ) != 0;
                                flagI = ( registerP & 4 ) != 0;
                                flagZ = ( registerP & 2 ) != 0;
                                flagC = ( registerP & 1 ) != 0;
                                break;
                            }
                        }
                        else if( rmw )
                        {
                            switch( opcode & 0xE0 )
                            {
                                case 0x00: // ASL
                                    flagC = tmp < 0;
                                    tmp2 = (byte)( tmp << 1 );
                                    break;

                                case 0x20: // ROL
                                    tmp2 = (byte)( tmp << 1 );
                                    if( flagC ) tmp2 |= 1;
                                    flagC = tmp < 0;
                                    break;

                                case 0x40: // LSR
                                    flagC = ( tmp & 1 ) != 0;
                                    tmp2 = (byte)( ( tmp & 0xFF ) >> 1 );
                                    break;

                                case 0x60: // ROR
                                    tmp2 = (byte)( ( tmp & 0xFF ) >> 1 );
                                    if( flagC ) tmp2 |= 0x80;
                                    flagC = ( tmp & 1 ) != 0;
                                    break;

                                case 0xC0: // DEC
                                    tmp2 = (byte)( tmp - 1 );
                                    break;

                                case 0xE0: // INC
                                    tmp2 = (byte)( tmp + 1 );
                                    break;
                            }

                            flagN = tmp2 < 0;
                            flagZ = tmp2 == 0 ;

                            switch( opcode & 0x1F )
                            {
                                case 0x06:
                                case 0x16:
                                    ram[ address ] = tmp2;
                                    break;

                                case 0x0E:
                                case 0x1E:
                                    poke( address , tmp2 );
                                    break;
                            }
                        }
                        else // !rmw
                        {
                            switch( opcode & 0xE0 )
                            {
                                case 0x00: // ORA
                                    a |= tmp;
                                    flagN = a < 0;
                                    flagZ = a == 0;
                                    break;

                                case 0x20: // AND
                                    a &= tmp;
                                    flagN = a < 0;
                                    flagZ = a == 0;
                                    break;

                                case 0x40: // EOR
                                    a ^= tmp;
                                    flagN = a < 0;
                                    flagZ = a == 0;
                                    break;

                                case 0x60: // ADC
                                    if( flagD )
                                    {
                                        int c = flagC ? 1 : 0;
                                        k = ( a & 0xF ) + ( tmp & 0xF ) + c;

                                        if( ( k & 0xFF ) >= 0xA ) k = ( k & 0xFF00 ) + ( ( k + 0x6 ) & 0xFF );
                                        if( ( k & 0xFF ) >= 0x20 ) k = ( k & 0xFF0F ) + 0x10;

                                        k += ( a & 0xF0 ) + ( tmp & 0xF0 );

                                        flagZ = ( 0xFF & ( a + tmp + c ) ) == 0 ;
                                        flagV = ( ~( ( 0xFF & (int)a ) ^ ( 0xFF & (int)tmp ) ) & ( ( 0xFF & (int)a ) ^ ( 0xFF & k ) ) & 0x80 ) != 0 ;
                                        flagC = false;
                                        if( k >= 0xA0 )
                                        {
                                            k += 0x60;
                                            flagC = true;
                                        }

                                        a = (byte)k;
                                        flagN = a < 0 ;
                                    }
                                    else
                                    {
                                        k = ( 0xFF & (int)a ) + ( 0xFF & (int)tmp ) + ( flagC ? 1 : 0 );
                                        flagV = ( ~( ( 0xFF & (int)a ) ^ ( 0xFF & (int)tmp ) ) & ( ( 0xFF & (int)a ) ^ ( 0xFF & k ) ) & 0x80 ) != 0;
                                        flagC = ( k & 0xFF00 ) != 0;
                                        a = (byte)k;
                                        flagZ = a == 0;
                                        flagN = a < 0;
                                    }
                                    break;

                                case 0xC0: // CMP
                                    flagN = (byte)( a - tmp ) < 0 ;
                                    flagZ = a == tmp ;
                                    flagC = ( 0xFF & (int)tmp ) <= ( 0xFF & (int)a );
                                    break;

                                case 0xE0: // SBC
                                    if( flagD )
                                    {
                                        int c = flagC ? 0 : 1;
                                        k = ( a & 0xF ) + 0x100;
                                        k -= ( tmp & 0xF ) + c;
                                        if( k < 0x100 ) k -= 6;
                                        if( k < 0xF0 ) k += 0x10;
                                        k += a & 0xF0;
                                        k -= tmp & 0xF0;

                                        flagC = true;
                                        flagN = ( k & 0x80 ) != 0 ;
                                        flagZ = ( a - tmp - c == 0 ) ;
                                        flagV = ( ~( ( 0xFF & k ) ^ tmp ) & ( a ^ tmp ) & 0x80 ) != 0 ;
                                        if( ( k & 0xFF00 ) == 0 )
                                        {
                                            k = ( k & 0xFF00 ) + ( 0xFF & ( ( k & 0xFF ) - 0x60 ) );
                                            flagC = false;
                                        }
                                        a = (byte)k;
                                    }
                                    else
                                    {
                                        k = 0x100 + ( 0xFF & (int)a ) - ( 0xFF & (int)tmp ) - ( flagC ? 0 : 1 );
                                        flagV = ( ~( ( 0xFF & k ) ^ tmp ) & ( a ^ tmp ) & 0x80 ) != 0;
                                        flagC = ( k & 0xFF00 ) != 0;
                                        a = (byte)k;
                                        flagN = a < 0;
                                        flagZ = a == 0;
                                    }
                                    break;
                            }
                        }
                    }
                }

/*
                        case 0x82:
                        case 0x80:
                        case 0xc2:
                        case 0xe2:
                        case 0x04:
                        case 0x14:
                        case 0x34:
                        case 0x44:
                        case 0x54:
                        case 0x64:
                        case 0x74:
                        case 0x89:  // op tests call it NOP #imm ??
                        case 0xD4:
                        case 0xf4:
                            pc++;
                            break;

                        case 0x0B:  // ASO_Imm
                            tmp = peek( pc++ );
                            if( ( tmp & 0x80 ) != 0 ) flagC = true;
                            tmp <<= 1;
                            a |= tmp;
                            flagN = a < 0;
                            flagZ = a == 0;
                            remaining_cycles = 4;
                            break;

                        case 0x1F:
                            address = deek( pc ) + ( 0xFF & x );
                            pc += 2;

                            flagC = ( peek( address ) & 0x80 ) != 0;
                            poke( address, tmp = (byte)( peek( address ) << 1 ) );
                            flagN = tmp < 0;
                            flagZ = tmp == 0;

                            a |= peek( address );
                            flagN = a < 0;
                            flagZ = a == 0;

                            remaining_cycles = 7;
                            break;

                        case 0x67:
                            address = 0xFF & peek( pc++ );

                            tmp = (byte)( peek( address ) >> 1 );
                            if( flagC ) tmp |= 0x80;
                            else tmp &= 0x7F;
                            flagC = ( peek( address ) & 1 ) != 0;
                            flagN = tmp < 0;
                            flagZ = tmp == 0;
                            poke( address, tmp );

                            if( flagD )
                            {
                                a = decimal_ADC( a , peek(address) , flagC );
                                flagN = registerP < 0;
                                flagV = ( registerP & 64 ) != 0;
                                flagZ = ( registerP & 2 ) != 0;
                                flagC = ( registerP & 1 ) != 0;
                            }
                            else
                            {
                                tmp = peek(address);
                                k = ( 0xFF & (int)a ) + ( 0xFF & (int)tmp ) + ( flagC ? 1 : 0 );
                                flagV = ( ~( ( 0xFF & (int)a ) ^ ( 0xFF & (int)tmp ) ) & ( ( 0xFF & (int)a ) ^ ( 0xFF & k ) ) & 0x80 ) != 0;
                                flagC = ( k & 0xFF00 ) != 0;
                                a = (byte)k;
                                flagZ = a == 0;
                                flagN = a < 0;
                            }
                            remaining_cycles = 5;
                            break;

                        case 0x87:
                            address = 0xFF & peek( pc++ );
                            poke( address, (byte)( a & x ) );
                            remaining_cycles = 5;
                            break;

                        case 0xBF:
                            address = deek( pc ) + ( 0xFF & y );
                            pc += 2;
                            a = x = peek( address );
                            flagN = a < 0;
                            flagZ = a == 0;
                            break;

                        case 0xC3:
                            address = 0xFF & (int)( peek( pc++ ) + x );
                            address = deek( address );

                            poke( address, tmp = (byte)( peek( address ) - 1 ) );
                            flagN = tmp < 0;
                            flagZ = tmp == 0;

                            tmp = peek( address );
                            flagN = (byte)( a - tmp ) < 0 ;
                            flagZ = a == tmp ;
                            flagC = ( 0xFF & (int)tmp ) <= ( 0xFF & (int)a );

                            remaining_cycles = 8;
                            break;

                        case 0xC7:
                            address = 0xFF & peek( pc++ );

                            poke( address, tmp = (byte)( peek( address ) - 1 ) );
                            flagN = tmp < 0;
                            flagZ = tmp == 0;

                            tmp = peek( address );
                            flagN = (byte)( a - tmp ) < 0 ;
                            flagZ = a == tmp ;
                            flagC = ( 0xFF & (int)tmp ) <= ( 0xFF & (int)a );

                            remaining_cycles = 6;
                            break;

                        case 0xFF:
                            address = deek( pc ) + ( 0xFF & x );
                            pc += 2;
                            poke( address, (byte)( peek(address) + 1 ) );
                            if( flagD )
                            {
                                a = decimal_SBC( a ,  peek(address) , flagC );
                                flagN = registerP < 0;
                                flagV = ( registerP & 64 ) != 0;
                                flagZ = ( registerP & 2 ) != 0;
                                flagC = ( registerP & 1 ) != 0;
                            }
                            else
                            {
                                tmp = peek(address);
                                k = 0x100 + ( 0xFF & (int)a ) - ( 0xFF & (int)tmp ) - ( flagC ? 0 : 1 );
                                flagV = ( ~( ( 0xFF & k ) ^ tmp ) & ( a ^ tmp ) & 0x80 ) != 0;
                                flagC = ( k & 0xFF00 ) != 0;
                                a = (byte)k;
                                flagN = a < 0;
                                flagZ = a == 0;
                            }
                            remaining_cycles = 8;
                            break;

                        case 0x0C:  // SKW
                        case 0x1C:
                        case 0x3C:
                        case 0x5C:
                        case 0x7C:
                        case 0xDC:
                        case 0xFC:
                            pc += 2;
                            break;
*/


                if( viaT1overflow )
                {
                    if( viaT1running )
                    {
                        viaT1running = ( viaACR & 0x40 ) != 0;
                        viaIFR |= 0x40;
                        viaIRQ = ( viaIER & viaIFR ) != 0;
                    }

                    viaT1C = viaT1L;
                    viaT1overflow = false;

                    // TO-DO: PB7 control
                }
                else
                {
                    viaT1C--;
                    if( viaT1C < 0 ) viaT1overflow = true;
                }

                if( ( viaACR & 0x20 ) == 0 )
                {
                    if( viaT2overflow && viaT2running )
                    {
                        viaT2running = false;
                        viaIFR |= 0x20;
                        viaIRQ = ( viaIER & viaIFR ) != 0;
                    }

                    viaT2C--;
                    viaT2overflow = ( viaT2C == -1 );
                }

                // TO-DO: SR operation
                // TO-DO: handshaking pulse mode


            }
        }
        registerPC = pc ;
        this.registerA = a;
        this.registerX = x;
        this.registerY = y;
        this.registerS = s;
        this.registerP = 0x30;
        if( flagN ) registerP |= 0x80;
        if( flagV ) registerP |= 0x40;
        if( flagD ) registerP |= 8;
        if( flagI ) registerP |= 4;
        if( flagZ ) registerP |= 2;
        if( flagC ) registerP |= 1;
    }


    private String hexToString( int val, int len )
    {
        String str = Integer.toHexString( val ).toUpperCase();

        if( str.length() > len ) str = str.substring( str.length() - len );
        else while( str.length() < len ) str = "0" + str;

        return str;
    }

    private byte peek( int address )
    {
        address &= 0xFFFF;

        if( address >= 0xC000 ) return rom[ address & 0x3FFF ];

        if( address < 0x0300 || address > 0x03FF ) return ram[ address ];

        address &= 0x0F;
        int val = 0;

        switch( address )
        {
            case 0: // IRB
                viaPortB = kbdKeyPressed ? 0xFF : 0xF7 ;
                val = viaDDRB & viaORB | ~viaDDRB & viaPortB; // TODO: handshaking, latching
                break;

            case 1: // IRA
                val = viaPortA; // TODO : handshaking, latching
                break;

            case 2: // DDRB
                val = viaDDRB;
                break;

            case 3: // DDRA
                val = viaDDRA;
                break;

            case 4: // T1C_L
                val = viaT1C & 0xFF;
                viaIFR &= ~0x40;
                viaIRQ = ( viaIER & viaIFR ) != 0;
                break;

            case 5: // T1C_H
                val = viaT1C >> 8;
                break;

            case 6: // T1L_L
                val = viaT1L & 0xFF;
                break;

            case 7: // T1L_H
                val = viaT1L >> 8;
                break;

            case 8: // T2C_L
                val = viaT2C & 0xFF;
                viaIFR &= ~0x20;
                viaIRQ = ( viaIER & viaIFR ) != 0;
                break;

            case 9: // T2C_H
                val = viaT2C >> 8;
                break;

            case 10: // SR
                val = viaSR;
                viaIFR &= ~0x04;
                viaIRQ = ( viaIER & viaIFR ) != 0;
                break;

            case 11: // ACR
                val = viaACR;
                break;

            case 12: // PCR
                val = viaPCR;
                break;

            case 13: // IFR
                if( viaIRQ )
                    val = viaIFR | 0x80;
                else
                    val = viaIFR;
                break;

            case 14: // IER
                val = viaIER;
                break;

            case 15: // IRA no handshake
                val = viaPortA; // TODO : latching
                break;
        }
        return (byte)val;
    }

    private int deek( int address )
    {
        return ( 0xFF & (int)peek( address ) ) + ( 0xFF00 & ( (int)peek( ( address + 1 ) & 0xFFFF ) << 8 ) );
    }

    private void poke( int address, byte val )
    {
        address &= 0xFFFF;

        if( address >= 0xC000 ) return;

        if( address < 0x0300 || address > 0x03FF ) {
            ram[ address ] = val;
            return;
        }

        address &= 0x0F;

        switch( address )
        {
            case 0: // ORB
                viaORB = val;  // TODO : handshaking
                kbdSelectedLine = (viaDDRB & viaORB | ~viaDDRB ) & 7;
                kbdKeyPressed = (keyboardMatrix[ kbdSelectedLine ] & kbdSelectedColumns) != 0 ;
                break;

            case 1: // ORA
                viaORA = val;  // TODO : handshaking
                break;

            case 2: // DDRB
                viaDDRB = val;
                break;

            case 3: // DDRA
                viaDDRA = val;
                break;

            case 4: // T1L_L
                viaT1L = viaT1L & 0xFF00 | val & 0x00FF;
                break;

            case 5: // T1C_H
                viaT1L = viaT1L & 0xFF | (val << 8) & 0xFF00;
                viaT1C = viaT1L + 1; // fake a 1 us delay before starting the counter
                viaT1running = true;
                viaT1overflow = false;
                viaIFR &= ~0x40;
                viaIRQ = ( viaIER & viaIFR ) != 0;
                break;

            case 6: // T1L_L
                viaT1L = viaT1L & 0xFF00 | val & 0x00FF;
                break;

            case 7: // T1L_H
                viaT1L = viaT1L & 0xFF | (val << 8) & 0xFF00;
                break;

            case 8: // T2L_L
                viaT2L = val & 0xFF;
                break;

            case 9: // T2C_H
                viaT2C = viaT2L + ((val << 8) & 0xFF00) + 1; // fake a 1 us delay
                viaT2running = true;
                viaT2overflow = false;
                viaIFR &= ~0x20;
                viaIRQ = ( viaIER & viaIFR ) != 0;
                break;

            case 10: // SR
                viaSR = val;    // TODO : SR operation
                break;

            case 11: // ACR
                viaACR = val;
                break;

            case 12: // PCR
                viaPCR = val;
                boolean oldBC1 = psgBC1;
                boolean oldBDIR = psgBDIR;
                psgBC1 = ( viaPCR & 0x0E ) != 0x0C;
                psgBDIR = ( viaPCR & 0xE0 ) != 0xC0;
                if( psgBC1 )
                {
                    if( !psgBDIR ) // BC1=1 and BDIR=0 on PSG control lines : read PSG
                        if ( psgIndex < 16 ) viaPortA = psgRegs[ psgIndex ];
                }
                else if( !psgBDIR ) // BC1=0 and BDIR=0 : return to PSG idle state
                {
                    if( oldBC1 && oldBDIR ) psgIndex = viaORA & 0xFF;
                    if( !oldBC1 && oldBDIR && psgIndex<16) writePSG( viaORA );
                }
                break;

            case 13: // IFR
                viaIFR &= ~val;
                viaIRQ = ( viaIER & viaIFR ) != 0;
                break;

            case 14: // IER
                if( val < 0 )
                    viaIER |= val & 0x7F;
                else
                    viaIER &= ~val;

                viaIRQ = ( viaIER & viaIFR ) != 0;
                break;

            case 15: // ORA no handshake
                viaORA = val;
                break;
        }

    }

    private void writePSG( int val )
    {
        if( psgIndex < 16 ) psgRegs[ psgIndex ] = (byte)val;

        switch( psgIndex )
        {

            case 0:     // PeriodA (low)
                psgA_period = psgA_period & 0x0F00 | val & 0xFF;
                break;

            case 1:     // PeriodA (high)
                psgA_period = psgA_period & 0xFF | (val << 8) & 0x0F00;
                break;

            case 2:     // PeriodB (low)
                psgB_period = psgB_period & 0x0F00 | val & 0xFF;
                break;

            case 3:     // PeriodB (high)
                psgB_period = psgB_period & 0xFF | (val << 8) & 0x0F00;
                break;

            case 4:     // PeriodC (low)
                psgC_period = psgC_period & 0x0F00 | val & 0xFF;
                break;

            case 5:     // PeriodC (high)
                psgC_period = psgC_period & 0xFF | (val << 8) & 0x0F00;
                break;

            case 6:     // PeriodN
                psgN_period = val & 0x1F;
                break;

            case 7:     // Control
                psgA_disabled = ( val & 1 ) != 0 ? -1 : 0;
                psgB_disabled = ( val & 2 ) != 0 ? -1 : 0;
                psgC_disabled = ( val & 4 ) != 0 ? -1 : 0;
                psgNA_disabled = ( val & 8 ) != 0 ? -1 : 0;
                psgNB_disabled = ( val & 16 ) != 0 ? -1 : 0;
                psgNC_disabled = ( val & 32 ) != 0 ? -1 : 0;
                psgIOA_dir = ( val & 64 ) != 0;
//              psgIOB_dir = ( val & 128 ) != 0;
                break;

            case 8:     // AmplitudeA
                psgA_envelop = ( val & 0x10 ) != 0;
                psgA_amplitude = psgA_envelop ? psgE_amplitude : val & 15;
                break;

            case 9:     // AmplitudeB
                psgB_envelop = ( val & 0x10 ) != 0;
                psgB_amplitude = psgB_envelop ? psgE_amplitude : val & 15;
                break;

            case 10:    // AmplitudeC
                psgC_envelop = ( val & 0x10 ) != 0;
                psgC_amplitude = psgC_envelop ? psgE_amplitude : val & 15;
                break;

            case 11:    // Envelop period (low)
                psgE_period = psgE_period & 0xFF00 | val & 0xFF ;
                break;

            case 12:    // Envelop period (high)
                psgE_period = psgE_period & 0xFF | (val << 8) & 0xFF00 ;
                break;

            case 13:    // Envelop shape
                psgE_hold = ( val & 1 ) != 0 ;
                psgE_alternate = ( val & 2 ) != 0 ;
                psgE_attack = ( val & 4 ) != 0 ;
                psgE_continue = ( val & 8 ) != 0;
                psgE_countup = psgE_attack ;
                psgE_countdown = !psgE_attack ;
                psgE_amplitude = psgE_attack ? 0 : 15 ;
                if (psgA_envelop) psgA_amplitude = psgE_amplitude;
                if (psgB_envelop) psgB_amplitude = psgE_amplitude;
                if (psgC_envelop) psgC_amplitude = psgE_amplitude;
                break;

            case 14:    // IO Port A
                kbdSelectedColumns = ~val ;
                if (psgIOA_dir)
                {
                    kbdSelectedLine = (viaDDRB & viaORB | ~viaDDRB ) & 7;
                    boolean keyPressed = (keyboardMatrix[ kbdSelectedLine ] & kbdSelectedColumns) != 0 ;
                }
                break;

            case 15:    // IO Port B
                break;
        }
    }

    private void updatePSG()
    {
        if( EMULATE_SOUND )
        {
            psgA_counter ++;
            if( psgA_counter >= psgA_period )
            {
                // output is inversed every period,
                // thus A_period is in fact half the period of the tone
                psgA_output = ~psgA_output;
                psgA_counter = 0;
            }

            psgB_counter ++;
            if( psgB_counter >= psgB_period )
            {
                psgB_output = ~psgB_output;
                psgB_counter = 0;
            }

            psgC_counter ++;
            if( psgC_counter >= psgC_period )
            {
                psgC_output = ~psgC_output;
                psgC_counter = 0;
            }

            psgN_counter ++;
            if( psgN_counter >= psgN_period )
            {
                psgN_output = ( psgN_shifter & 1 ) != 0 ? -1 : 0;
                psgN_counter = 0;
                psgN_shifter >>= 1;
                if( ((psgN_shifter ^ psgN_output) & 2) != 0 ) psgN_shifter |= 0x10000;
            }

           // computing envelop amplitude, and thus each chanel's amplitude
           psgE_counter++;
           if( psgE_counter >= psgE_period )
           {
               int amplitude = psgE_amplitude;
               psgE_counter = 0;
               if( psgE_countup ) amplitude ++;
               if( psgE_countdown ) amplitude --;
               if( amplitude==16 || amplitude==-1 )
               {
/* graphical representation of envelopes
 *
 *  00xx		\__________________________________
 *
 *  01xx		/|_________________________________
 *
 *  1000		\|\|\|\|\|\|\|\|\|\|\|\|\|\|\|\|\|\
 *
 *  1001		\__________________________________
 *
 *  1010		\/\/\/\/\/\/\/\/\/\/\/\/\/\/\/\/\/\
 *			      _________________________________
 *  1011		\|
 *
 *  1100		/|/|/|/|/|/|/|/|/|/|/|/|/|/|/|/|/|/
 *			     __________________________________
 *  1101		/
 *
 *  1110		/\/\/\/\/\/\/\/\/\/\/\/\/\/\/\/\/\/
 *
 *  1111		/|_________________________________
 *
 */
                   if( !psgE_continue )
                   {
                       psgE_countup = psgE_countdown = false;
                       psgE_amplitude = 0;
                   }
                   else if( psgE_hold )
                   {
                       psgE_countup = psgE_countdown = false;
                       if( psgE_alternate )
                           psgE_amplitude = psgE_attack ? 0 : 15;
                       else
                           psgE_amplitude = psgE_attack ? 15 : 0;
                   }
                   else if( psgE_alternate )
                   {
                       psgE_countup = !psgE_countup;
                       psgE_countdown = !psgE_countdown;
                       psgE_amplitude= psgE_countup ? 0 : 15;
                   }
                   else
                   {
                       psgE_amplitude = amplitude & 15;
                   }
               }
               else psgE_amplitude = amplitude;

               if (psgA_envelop) psgA_amplitude = psgE_amplitude;
               if (psgB_envelop) psgB_amplitude = psgE_amplitude;
               if (psgC_envelop) psgC_amplitude = psgE_amplitude;
           }

           // mixing noise and tone generators, and applying amplitudes to compute the 3 chanels
           psgA_chanel[ psgOutIndex ] = volume[ (psgA_output | psgA_disabled) & (psgN_output | psgNA_disabled) & psgA_amplitude];
           psgB_chanel[ psgOutIndex ] = volume[ (psgB_output | psgB_disabled) & (psgN_output | psgNB_disabled) & psgB_amplitude];
           psgC_chanel[ psgOutIndex ] = volume[ (psgC_output | psgC_disabled) & (psgN_output | psgNC_disabled) & psgC_amplitude];
           psgOutIndex ++ ;
       }
    }

    private void writeAudio()
    {
        try
        {
            int cycles = pal_freq ? 312*64 : 264*64 ;
            int values = cycles / 16;
            int samples = (int)(cycles * audioFreq / 1E6);
            float valuesPerSample = values / (float)samples;
            float valueIndex = 0;
            for( int i=0 ; i < samples ; i++ )
            {
                 audioBufA[ i ] = psgA_chanel[ (int)valueIndex ];
                 audioBufB[ i ] = psgB_chanel[ (int)valueIndex ];
                 audioBufC[ i ] = psgC_chanel[ (int)valueIndex ];
                 valueIndex += valuesPerSample;
            }
            if( waveOutA.available() < samples )
            {
                Thread.sleep( 5 );
            }
            waveOutA.write( audioBufA, 0, samples );
            waveOutB.write( audioBufB, 0, samples );
            waveOutC.write( audioBufC, 0, samples );
        }
        catch( InterruptedException e )
        {
            errorMessage( "ERROR. Main thread interrupted." );
        }
    }

    private void timeSynchro()
    {
        long currentTime = System.currentTimeMillis();
        try
        {
            if( currentTime < frameTime + 20 )
            {
                Thread.sleep( frameTime + 20 - currentTime );
                currentTime = System.currentTimeMillis();
            }
        }
        catch( InterruptedException e )
        {
            errorMessage( "ERROR. Main thread interrupted." );
            currentTime = System.currentTimeMillis();
        }
        frameTime = currentTime;
    }
}

