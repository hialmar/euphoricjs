import {AfterViewInit, Component, ElementRef, ViewChild} from '@angular/core';
import {HttpClient} from '@angular/common/http';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements AfterViewInit {

  constructor(private httpClient: HttpClient) {
    this.loadRom();
  }
  title = 'Euphoric-JS 0.1';
  @ViewChild('myCanvas') canvasRef: ElementRef;
  private ctx: CanvasRenderingContext2D;

  private EMULATE_SOUND = false;
  private palette = [0x000000FF, 0xFF0000FF, 0x00FF00FF, 0xFFFF00FF, 0x0000FFFF, 0xFF00FFFF, 0x00FFFFFF, 0xFFFFFFFF];

  private frameTime = 0;
  private initialized = false;

  private pixelBuffer = new Uint8ClampedArray(240 * 224 * 4);

  private scale = 2;
  private bufferX = 0;
  private bufferY = 0;
  private drawBorders = true;
  private frameSkip = 0;


  // Rom & Ram
  private rom: Uint8ClampedArray;
  private ram = new Uint8ClampedArray(48 * 1024 );
  /* ULA state */
  private graphMode = false;
  private frameCount = 0;
  private palFreq = true;
  /* VIA state */
  private viaDDRB: number;
  private viaORB: number;
  private viaPortB: number;
  private viaDDRA: number;
  private viaORA: number;
  private viaPortA: number;
  private viaT1C: number;
  private viaT1L: number;
  private viaT2C: number;
  private viaT2L: number;
  private viaT1overflow: boolean;
  private viaT1running: boolean;
  private viaT2overflow: boolean;
  private viaT2running: boolean;
  private viaSR: number;
  private viaACR: number;
  private viaPCR: number;
  private viaIER: number;
  private viaIFR: number;
  private viaIRQ: boolean;
  /* PSG state */
  private psgIndex = 15;
  private psgRegs = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0];
  private psgBC1 = false;
  private psgBDIR = false;
  private psgAPeriod: number;
  private psgBPeriod: number;
  private psgCPeriod: number;
  private psgNPeriod: number;
  private psgEPeriod: number;
  private psgADisabled: number;
  private psgBDisabled: number;
  private psgCDisabled: number;
  private psgNADisabled: number;
  private psgNBDisabled: number;
  private psgNCDisabled: number;
  private psgIOADir: boolean;
  private psgAEnvelop: boolean;
  private psgBEnvelop: boolean;
  private psgCEnvelop: boolean;
  private psgAAmplitude: number;
  private psgBAmplitude: number;
  private psgCAmplitude: number;
  private psgEAmplitude: number;
  private psgEHold: boolean;
  private psgEAlternate: boolean;
  private psgEAttack: boolean;
  private psgEContinue: boolean;
  private psgECountUp: boolean;
  private psgECountDown: boolean;
  private psgACounter: number;
  private psgBCounter: number;
  private psgCCounter: number;
  private psgNCounter: number;
  private psgECounter: number;
  private psgAOutput: number;
  private psgBOutput: number;
  private psgCOutput: number;
  private psgNOutput: number;
  private psgNShifter: number;
  private psgAChanel = new Uint8ClampedArray( 1248 );   // need 1248 values per Frame
  private psgBChanel = new Uint8ClampedArray( 1248 );   // need 1248 values per Frame
  private psgCChanel = new Uint8ClampedArray( 1248 );   // need 1248 values per Frame
  private psgOutIndex = 0;
  /* audio state */
  private audioFreq = 22050;
  private volume = [0, 2, 3, 4, 6, 8, 11, 16, 23, 32, 45, 64, 90, 128, 181, 255];
  private audioBufA = new Uint8ClampedArray( 440 );  // need 440 bytes per Frame at 22050 Hz
  private audioBufB = new Uint8ClampedArray( 440 );  // need 440 bytes per Frame at 22050 Hz
  private audioBufC = new Uint8ClampedArray( 440 );  // need 440 bytes per Frame at 22050 Hz
  /* keyboard state */
  private kbdSelectedColumns = 0;
  private kbdSelectedLine = 0;
  private kbdKeyPressed = false;
  /* tape emulation */
  private tapeBoot = false;
  private tapeLoading = true;
  private tapeFileNameAddr = 0x27F;
  private tapeNameFoundAddr = 0x293;
  private tapeHeaderAddr = 0x2A7;

// CPU State
  private nmi = false;
  private reset = false;
  public registerPC: number;
  public registerA = 0;
  public registerX = 0;
  public registerY = 0;
  public registerS = 0xFF;
  public registerP;
  private keyboardMatrix = new Uint8ClampedArray( 1024 ); // TODO

  drawing = false;

  startTime = Date.now();
  time = 0;
  play: boolean;

  loadRom(): void {
    this.httpClient.get('./assets/BASIC11B.ROM', {responseType: 'blob'}).subscribe(
      (response) => {
        if (response) {
          response.arrayBuffer().then((arrayBuffer) => {
            this.rom = new Uint8ClampedArray(arrayBuffer);
            console.log(this.rom);
          });
        }
      }, (error) => {
        console.log(error);
      });
  }

  private peek(address: number): number {
    address &= 0xFFFF;

    if (address >= 0xC000) {
      return this.rom[address & 0x3FFF];
    }

    if (address < 0x0300 || address > 0x03FF) {
      return this.ram[address];
    }

    address &= 0x0F;
    let val = 0;

    switch (address) {
      case 0: // IRB
        this.viaPortB = this.kbdKeyPressed ? 0xFF : 0xF7;
        val = this.viaDDRB & this.viaORB | ~this.viaDDRB & this.viaPortB; // TODO: handshaking, latching
        break;

      case 1: // IRA
        val = this.viaPortA; // TODO : handshaking, latching
        break;

      case 2: // DDRB
        val = this.viaDDRB;
        break;

      case 3: // DDRA
        val = this.viaDDRA;
        break;

      case 4: // T1C_L
        val = this.viaT1C & 0xFF;
        this.viaIFR &= ~0x40;
        this.viaIRQ = (this.viaIER & this.viaIFR) !== 0;
        break;

      case 5: // T1C_H
        val = this.viaT1C >> 8;
        break;

      case 6: // T1L_L
        val = this.viaT1L & 0xFF;
        break;

      case 7: // T1L_H
        val = this.viaT1L >> 8;
        break;

      case 8: // T2C_L
        val = this.viaT2C & 0xFF;
        this.viaIFR &= ~0x20;
        this.viaIRQ = (this.viaIER & this.viaIFR) !== 0;
        break;

      case 9: // T2C_H
        val = this.viaT2C >> 8;
        break;

      case 10: // SR
        val = this.viaSR;
        this.viaIFR &= ~0x04;
        this.viaIRQ = (this.viaIER & this.viaIFR) !== 0;
        break;

      case 11: // ACR
        val = this.viaACR;
        break;

      case 12: // PCR
        val = this.viaPCR;
        break;

      case 13: // IFR
        if (this.viaIRQ) {
          val = this.viaIFR | 0x80;
        } else {
          val = this.viaIFR;
        }
        break;

      case 14: // IER
        val = this.viaIER;
        break;

      case 15: // IRA no handshake
        val = this.viaPortA; // TODO : latching
        break;
    }
    return val;
  }

  private deek(address: number): number {
    return (0xFF & this.peek(address)) + (0xFF00 & (this.peek((address + 1) & 0xFFFF) << 8));
  }

  private poke(address: number, val: number): void {
    address &= 0xFFFF;

    if (address >= 0xC000) {
      return;
    }

    if (address < 0x0300 || address > 0x03FF) {
      this.ram[address] = val;
      return;
    }

    address &= 0x0F;

    switch (address) {
      case 0: // ORB
        this.viaORB = val;  // TODO : handshaking
        this.kbdSelectedLine = (this.viaDDRB & this.viaORB | ~this.viaDDRB) & 7;
        this.kbdKeyPressed = (this.keyboardMatrix[this.kbdSelectedLine] & this.kbdSelectedColumns) !== 0;
        break;

      case 1: // ORA
        this.viaORA = val;  // TODO : handshaking
        break;

      case 2: // DDRB
        this.viaDDRB = val;
        break;

      case 3: // DDRA
        this.viaDDRA = val;
        break;

      case 4: // T1L_L
        this.viaT1L = this.viaT1L & 0xFF00 | val & 0x00FF;
        break;

      case 5: // T1C_H
        this.viaT1L = this.viaT1L & 0xFF | (val << 8) & 0xFF00;
        this.viaT1C = this.viaT1L + 1; // fake a 1 us delay before starting the counter
        this.viaT1running = true;
        this.viaT1overflow = false;
        this.viaIFR &= ~0x40;
        this.viaIRQ = (this.viaIER & this.viaIFR) !== 0;
        break;

      case 6: // T1L_L
        this.viaT1L = this.viaT1L & 0xFF00 | val & 0x00FF;
        break;

      case 7: // T1L_H
        this.viaT1L = this.viaT1L & 0xFF | (val << 8) & 0xFF00;
        break;

      case 8: // T2L_L
        this.viaT2L = val & 0xFF;
        break;

      case 9: // T2C_H
        this.viaT2C = this.viaT2L + ((val << 8) & 0xFF00) + 1; // fake a 1 us delay
        this.viaT2running = true;
        this.viaT2overflow = false;
        this.viaIFR &= ~0x20;
        this.viaIRQ = (this.viaIER & this.viaIFR) !== 0;
        break;

      case 10: // SR
        this.viaSR = val;    // TODO : SR operation
        break;

      case 11: // ACR
        this.viaACR = val;
        break;

      case 12: // PCR
        this.viaPCR = val;
        const oldBC1 = this.psgBC1;
        const oldBDIR = this.psgBDIR;
        this.psgBC1 = (this.viaPCR & 0x0E) !== 0x0C;
        this.psgBDIR = (this.viaPCR & 0xE0) !== 0xC0;
        if (this.psgBC1) {
          if (!this.psgBDIR) { // BC1=1 and BDIR=0 on PSG control lines : read PSG
            if (this.psgIndex < 16) {
              this.viaPortA = this.psgRegs[this.psgIndex];
            }
          }
        } else if (!this.psgBDIR) // BC1=0 and BDIR=0 : return to PSG idle state
        {
          if (oldBC1 && oldBDIR) {
            this.psgIndex = this.viaORA & 0xFF;
          }
          if (!oldBC1 && oldBDIR && this.psgIndex < 16) {
            this.writePSG(this.viaORA);
          }
        }
        break;

      case 13: // IFR
        this.viaIFR &= ~val;
        this.viaIRQ = (this.viaIER & this.viaIFR) !== 0;
        break;

      case 14: // IER
        if (val < 0) {
          this.viaIER |= val & 0x7F;
        } else {
          this.viaIER &= ~val;
        }

        this.viaIRQ = (this.viaIER & this.viaIFR) !== 0;
        break;

      case 15: // ORA no handshake
        this.viaORA = val;
        break;
    }

  }


  animation(): void {
    if (!this.play) { return; }
    this.time = Date.now() - this.startTime;
    let x = this.time;
    let y = 0;
    while (x > 400) {
      y += 1;
      x -= 400;
    }
    this.drawAt(x, y);
    this.run();
  }
  startTimer(): void {
    this.play = true;
    window.setInterval(() => this.animation(), 1);
  }

  pauseTimer(): void {
    this.play = false;
  }

  ngAfterViewInit(): void {
    this.ctx = this.canvasRef.nativeElement.getContext('2d');
    this.drawBorder();
    this.drawPoint();
  }

  startDrawing(evt: MouseEvent): void {
    this.drawing = true;
  }

  stopDrawing(evt: MouseEvent): void {
    this.drawing = false;
  }

  keepDrawing(evt: MouseEvent): void {
    if (!this.drawing) { return; }
    const x = evt.offsetX;
    const y = evt.offsetY;
    const imageData = this.ctx.getImageData(x, y, 2, 2);
    const data = imageData.data;
    for (let i = 0; i < data.length; i += 4) {
      data[i] = 255; // red
      data[i + 1] = 0; // green
      data[i + 2] = 0; // blue
      data[i + 3] = 255; // alpha
    }
    this.ctx.putImageData(imageData, x, y);
  }

  drawAt(x: number, y: number): void {
    const imageData = this.ctx.getImageData(x, y, 2, 2);
    const data = imageData.data;
    for (let i = 0; i < data.length; i += 4) {
      data[i] = 0; // red
      data[i + 1] = 255; // green
      data[i + 2] = 0; // blue
      data[i + 3] = 255; // alpha
    }
    this.ctx.putImageData(imageData, x, y);
  }

  clickMe(): void {
    this.ctx.fillStyle = 'rgb(200, 0, 0)';
    this.ctx.fillRect(10, 10, 50, 50);
    this.ctx.fillStyle = 'rgba(0, 0, 200, 0.5)';
    this.ctx.fillRect(30, 30, 50, 50);
  }

  drawPoint(): void {
    this.ctx.beginPath();
    this.ctx.arc(100, 100, 30, 0, 2 * Math.PI);
    this.ctx.fillStyle = 'darkred';
    this.ctx.fill();
  }

  drawBorder(): void {
    this.ctx.beginPath();
    this.ctx.moveTo(0, 0);
    this.ctx.lineTo(400, 0);
    this.ctx.lineTo(400, 300);
    this.ctx.lineTo(0, 300);
    this.ctx.lineTo(0, 0);
    this.ctx.stroke();
  }

  public run(): void
  {
    if ( !this.initialized )
    {
      // requestFocus();
      // addKeyListener( this );
      // keyboardInitialize();

      // bufferImage = createImage( new MemoryImageSource( 240, 224, pixelBuffer, 0, 240 ) );

      // soundInitialize();

      if (!this.initialize()) { return; }

      this.initialized = true;
    }


    {
      this.calculateFrame( 1 + this.frameSkip );

      console.log(this.registerPC);

      const imageData = new ImageData(this.pixelBuffer, 240, 224);
      this.ctx.putImageData(imageData, 0, 0);
      /*
      Graphics gfx = getGraphics();
      if( gfx != null )
      {
        bufferImage.flush();
        if( scale == 1 ) gfx.drawImage( bufferImage, bufferX, bufferY, this );
        else gfx.drawImage( bufferImage, bufferX, bufferY, 240 * scale, 224 * scale, this );

       */



 /*       if( drawBorders )
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
        } */

    }

    // if( frame != null ) frame.dispose();
  }

  private writePSG(val: number): void {
    if (this.psgIndex < 16) {
      this.psgRegs[this.psgIndex] = val;
    }

    switch (this.psgIndex) {

      case 0:     // PeriodA (low)
        this.psgAPeriod = this.psgAPeriod & 0x0F00 | val & 0xFF;
        break;

      case 1:     // PeriodA (high)
        this.psgAPeriod = this.psgAPeriod & 0xFF | (val << 8) & 0x0F00;
        break;

      case 2:     // PeriodB (low)
        this.psgBPeriod = this.psgBPeriod & 0x0F00 | val & 0xFF;
        break;

      case 3:     // PeriodB (high)
        this.psgBPeriod = this.psgBPeriod & 0xFF | (val << 8) & 0x0F00;
        break;

      case 4:     // PeriodC (low)
        this.psgCPeriod = this.psgCPeriod & 0x0F00 | val & 0xFF;
        break;

      case 5:     // PeriodC (high)
        this.psgCPeriod = this.psgCPeriod & 0xFF | (val << 8) & 0x0F00;
        break;

      case 6:     // PeriodN
        this.psgNPeriod = val & 0x1F;
        break;

      case 7:     // Control
        this.psgADisabled = (val & 1) !== 0 ? -1 : 0;
        this.psgBDisabled = (val & 2) !== 0 ? -1 : 0;
        this.psgCDisabled = (val & 4) !== 0 ? -1 : 0;
        this.psgNADisabled = (val & 8) !== 0 ? -1 : 0;
        this.psgNBDisabled = (val & 16) !== 0 ? -1 : 0;
        this.psgNCDisabled = (val & 32) !== 0 ? -1 : 0;
        this.psgIOADir = (val & 64) !== 0;
//              this.psgIOBDir = ( val & 128 ) !== 0;
        break;

      case 8:     // AmplitudeA
        this.psgAEnvelop = (val & 0x10) !== 0;
        this.psgAAmplitude = this.psgAEnvelop ? this.psgEAmplitude : val & 15;
        break;

      case 9:     // AmplitudeB
        this.psgBEnvelop = (val & 0x10) !== 0;
        this.psgBAmplitude = this.psgBEnvelop ? this.psgEAmplitude : val & 15;
        break;

      case 10:    // AmplitudeC
        this.psgCEnvelop = (val & 0x10) !== 0;
        this.psgCAmplitude = this.psgCEnvelop ? this.psgEAmplitude : val & 15;
        break;

      case 11:    // Envelop period (low)
        this.psgEPeriod = this.psgEPeriod & 0xFF00 | val & 0xFF;
        break;

      case 12:    // Envelop period (high)
        this.psgEPeriod = this.psgEPeriod & 0xFF | (val << 8) & 0xFF00;
        break;

      case 13:    // Envelop shape
        this.psgEHold = (val & 1) !== 0;
        this.psgEAlternate = (val & 2) !== 0;
        this.psgEAttack = (val & 4) !== 0;
        this.psgEContinue = (val & 8) !== 0;
        this.psgECountUp = this.psgEAttack;
        this.psgECountDown = !this.psgEAttack;
        this.psgEAmplitude = this.psgEAttack ? 0 : 15;
        if (this.psgAEnvelop) {
          this.psgAAmplitude = this.psgEAmplitude;
        }
        if (this.psgBEnvelop) {
          this.psgBAmplitude = this.psgEAmplitude;
        }
        if (this.psgCEnvelop) {
          this.psgCAmplitude = this.psgEAmplitude;
        }
        break;

      case 14:    // IO Port A
        this.kbdSelectedColumns = ~val;
        if (this.psgIOADir) {
          this.kbdSelectedLine = (this.viaDDRB & this.viaORB | ~this.viaDDRB) & 7;
          // keyPressed = (keyboardMatrix[ kbdSelectedLine ] & kbdSelectedColumns) !== 0 ;
        }
        break;

      case 15:    // IO Port B
        break;
    }
  }

  public hexToString(val: number, len: number): string {
    let str = val.toString(16).toUpperCase();

    if (str.length > len) {
      str = str.substring(str.length - len);
    } else {
      while (str.length < len) {
        str = '0' + str;
      }
    }

    return str;
  }

  private writeAudio(): void {

  }

  private timeSynchro(): void {

  }

  private initialize(): boolean
  {
    try
    {
      this.loadRom();
    }
    catch ( ex: any )
    {
      return false;
    }

    /*
    if( frame == null )
    {
      showStatus(appletName);
      try
      {
        String filename = getParameter("tape");
        if (filename!=null)
        {
          tapeBoot = true;
          tapeStream = openFile( filename );
        }
      }
      catch (Exception e)
      {
      }
    }*/

    if ( this.deek(0xFFFC) === 0xF88F )
    {
      if (this.tapeBoot) { this.rom[ 0x0592 ] = 2; }
      this.tapeFileNameAddr = 0x27F;
      this.tapeNameFoundAddr = 0x293;
      this.tapeHeaderAddr = 0x2A7;
      this.rom[0x2735] = this.rom[0x26C9] = 2;

      // if( frame != null )
      this.rom[0x275E] = this.rom[0x265E] = 2;
    }

    if ( this.deek(0xFFFC) === 0xF42D )
    {
      if (this.tapeBoot) { this.rom[ 0x05A2 ] = 2; }
      this.tapeFileNameAddr = 0x35;
      this.tapeNameFoundAddr = 0x49;
      this.tapeHeaderAddr = 0x5D;
      this.rom[0x2696] = this.rom[0x2630] = 2;

      // if ( frame != null ) {
      this.rom[0x26BE] = this.rom[0x25C6] = 2;
      // }
    }

    for ( let i = 0; i < this.ram.length; i++ )
    {
      if ( ( i & 0x80 ) !== 0 ) { this.ram[ i ] = 0xFF; }
      else { this.ram[ i ] = 0x00; }
    }

    this.viaORB = this.viaDDRB = 0;
    this.viaORA = this.viaDDRA = 0;
    this.viaACR = this.viaPCR = this.viaSR = 0;
    this.viaIFR = this.viaIER = 0;
    this.viaT1L = this.viaT1C = this.viaT2L = this.viaT2C = 0;
    this.viaT1overflow = this.viaT1running = false;
    this.viaT2overflow = this.viaT2running = false;
    this.viaIRQ = false;

    this.psgAOutput = this.psgBOutput = this.psgCOutput = this.psgNOutput = 0;
    this.psgAPeriod = this.psgBPeriod = this.psgCPeriod = this.psgNPeriod = 0;
    this.psgADisabled = this.psgBDisabled = this.psgCDisabled = 0;
    this.psgNADisabled = this.psgNBDisabled = this.psgNCDisabled = 0;
    this.psgNShifter = 1;

    this.registerPC = this.deek(0xFFFC);
    this.registerP = 0x20 ;

    return true;
  }

  private calculateFrame(nbFrames: number): void {
    let psgCounter = 16;
    // tslint:disable-next-line:variable-name
    let remaining_cycles = 0;
    let pc = this.registerPC;
    let address = 0;
    let tmp = 0;
    let tmp2 = 0;
    let a = this.registerA;
    let x = this.registerX;
    let y = this.registerY;
    let s = this.registerS;
    let flagN = this.registerP < 0;
    let flagZ = (this.registerP & 2) !== 0;
    let flagV = (this.registerP & 64) !== 0;
    let flagD = (this.registerP & 8) !== 0;
    let flagI = (this.registerP & 4) !== 0;
    let flagC = (this.registerP & 1) !== 0;
    let k;

    while (nbFrames !== 0) {
      nbFrames--;
      if (this.EMULATE_SOUND) {
        this.writeAudio();
      } // TODO
      else {
        this.timeSynchro();
      } // TODO ???
      this.psgOutIndex = 0;

      let column = 0;
      let dot_ink: number;
      let dot_paper: number;
      let pattern;
      const screen = this.pixelBuffer;
      let screenIndex = 0;
      let line = 0;
      let charline = 0;
      let ink = this.palette[7];
      let paper = this.palette[0];
      let total_lines = this.palFreq ? 312 : 264;
      let blink = false;
      let dbl_height = false;
      let blink_mask = 0x3F;
      let charset_base = this.graphMode ? 0x9800 : 0xB400;
      let charset = 0;
      let charset_addr = charset_base;
      let char_display = !this.graphMode;
      let line_addr = char_display ? 0xBB80 : 0xA000;
      this.frameCount++;
      let blank_lines = false;
      let frameDone = false;

      while (!frameDone) {

        if (nbFrames === 0 && !blank_lines && column < 40) {

          const videobyte = this.ram[line_addr + column];
          pattern = videobyte;

          if (char_display) {
            pattern = this.ram[charset_addr + ((videobyte & 0x7F) << 3) + charline];
          }

          if ((videobyte & 0x60) === 0) {
            pattern = 0;
            switch (videobyte & 0x18) {

              case 0:
                ink = this.palette[videobyte & 7];
                break;

              case 8:
                charset = videobyte & 1;
                charset_addr = charset_base + (charset << 10);
                dbl_height = (videobyte & 2) !== 0;
                charline = dbl_height ? (line & 15) >> 1 : line & 7;
                blink = (videobyte & 4) !== 0;
                blink_mask = (blink && (this.frameCount & 0x10) !== 0) ? 0 : 0x3F;
                break;

              case 0x10:
                paper = this.palette[videobyte & 7];
                break;

              case 0x18:
                this.palFreq = (videobyte & 2) !== 0;
                total_lines = this.palFreq ? 312 : 264;
                this.graphMode = (videobyte & 4) !== 0;
                charset_base = this.graphMode ? 0x9800 : 0xB400;
                charset_addr = charset_base + (charset << 10);
                char_display = !this.graphMode || line >= 200;
                if (char_display) {
                  line_addr = 0xBB80 + (line >> 3) * 40;
                } else {
                  line_addr = 0xA000 + line * 40;
                }
                break;
            }
          } else {
            pattern &= blink_mask;
          }

          if (videobyte < 0) {
            dot_ink = ink ^ 0x00FFFFFF;
            dot_paper = paper ^ 0x00FFFFFF;
          } else {
            dot_ink = ink;
            dot_paper = paper;
          }
          screen[screenIndex++] = (pattern & 0x20) !== 0 ? dot_ink : dot_paper;
          screen[screenIndex++] = (pattern & 0x10) !== 0 ? dot_ink : dot_paper;
          screen[screenIndex++] = (pattern & 0x08) !== 0 ? dot_ink : dot_paper;
          screen[screenIndex++] = (pattern & 0x04) !== 0 ? dot_ink : dot_paper;
          screen[screenIndex++] = (pattern & 0x02) !== 0 ? dot_ink : dot_paper;
          screen[screenIndex++] = (pattern & 0x01) !== 0 ? dot_ink : dot_paper;

        }

        column++;

        if (column === 64) {
          column = 0;
          line++;

          if (line < 224) {

            charline = line & 7;
            column = 0;
            ink = this.palette[7];
            paper = this.palette[0];
            blink = false;
            blink_mask = 0x3F;
            dbl_height = false;
            charset = 0;
            charset_addr = charset_base;

            if (line === 200) {
              char_display = true;
            }

            if (char_display) {
              line_addr = 0xBB80 + (line >> 3) * 40;
            } else {
              line_addr = 0xA000 + line * 40;
            }

          } else if (line === total_lines) {
            frameDone = true;
          } else {
            blank_lines = true;
          }
        }

        psgCounter--;
        if (psgCounter === 0) {
          psgCounter = 16;
          this.updatePSG();
        }

        if (remaining_cycles > 0) {
          remaining_cycles--;
        } else {
          if (this.viaIRQ) {
            if (!flagI) {
              tmp = 0x30;
              if (flagN) {
                tmp += 128;
              }
              if (flagV) {
                tmp += 64;
              }
              if (flagD) {
                tmp += 8;
              }
              if (flagI) {
                tmp += 4;
              }
              if (flagZ) {
                tmp += 2;
              }
              if (flagC) {
                tmp += 1;
              }

              this.ram[0x100 + (0xFF & s--)] = (pc >> 8);
              this.ram[0x100 + (0xFF & s--)] = pc;
              this.ram[0x100 + (0xFF & s--)] = (tmp & ~0x10);
              pc = this.deek(0xFFFE);
              flagI = true;
            }
          }

          if (this.nmi) {
            this.nmi = false;

            if (this.reset) {
              this.reset = false;
              this.initialize();
              pc = this.registerPC;
              flagN = this.registerP < 0;
              flagZ = (this.registerP & 2) !== 0;
              flagV = (this.registerP & 64) !== 0;
              flagD = (this.registerP & 8) !== 0;
              flagI = (this.registerP & 4) !== 0;
              flagC = (this.registerP & 1) !== 0;
            } else {
              tmp = 0x30;
              if (flagN) {
                tmp += 128;
              }
              if (flagV) {
                tmp += 64;
              }
              if (flagD) {
                tmp += 8;
              }
              if (flagI) {
                tmp += 4;
              }
              if (flagZ) {
                tmp += 2;
              }
              if (flagC) {
                tmp += 1;
              }

              this.ram[0x100 + (0xFF & s--)] = (pc >> 8);
              this.ram[0x100 + (0xFF & s--)] = pc;
              this.ram[0x100 + (0xFF & s--)] = tmp;
              pc = this.deek(0xFFFA);
            }
          }

          let zp_address: number;
          const opcode = this.peek(pc++);

          if ((opcode & 0xC0) === 0x80) {
            remaining_cycles = 1;

            switch (opcode & 0xFF) {
              case 0x81: // STA Indexed X Indirect
                zp_address = 0xFF & (this.peek(pc++) + x);
                this.poke(this.ram[zp_address] & 0xFF | (this.ram[zp_address + 1] << 8) & 0xFF00, a);
                remaining_cycles = 5;
                break;

              case 0x84: // STY Zero page
                this.ram[this.peek(pc++) & 0xFF] = y;
                remaining_cycles = 2;
                break;

              case 0x85: // STA Zero page
                this.ram[this.peek(pc++) & 0xFF] = a;
                remaining_cycles = 2;
                break;

              case 0x86: // STX Zero page
                this.ram[this.peek(pc++) & 0xFF] = x;
                remaining_cycles = 2;
                break;

              case 0x88: // DEY
                y--;
                flagN = y < 0;
                flagZ = y === 0;
                break;

              case 0x8A: // TXA
                a = x;
                flagN = a < 0;
                flagZ = a === 0;
                break;

              case 0x8C: // STY Absolute
                this.poke(this.peek(pc++) & 0xFF | (this.peek(pc++) << 8) & 0xFF00, y);
                remaining_cycles = 3;
                break;

              case 0x8D: // STA Absolute
                this.poke(this.peek(pc++) & 0xFF | (this.peek(pc++) << 8) & 0xFF00, a);
                remaining_cycles = 3;
                break;

              case 0x8E: // STX Absolute
                this.poke(this.peek(pc++) & 0xFF | (this.peek(pc++) << 8) & 0xFF00, x);
                remaining_cycles = 3;
                break;

              case 0x90: // BCC
                if (!flagC) {
                  pc += this.peek(pc);
                  remaining_cycles = 2;
                }
                pc++;
                break;

              case 0x91: // STA (),Y
                zp_address = 0xFF & this.peek(pc++);
                this.poke((this.ram[zp_address] & 0xFF | (this.ram[zp_address + 1] << 8) & 0xFF00) + (0xFF & y), a);
                remaining_cycles = 5;
                break;

              case 0x94: // STY Page Zero Indexed X
                this.ram[0xFF & (this.peek(pc++) + x)] = y;
                remaining_cycles = 3;
                break;

              case 0x95: // STA Page Zero Indexed X
                this.ram[0xFF & (this.peek(pc++) + x)] = a;
                remaining_cycles = 3;
                break;

              case 0x96: // STX Page Zero Indexed Y
                this.ram[0xFF & (this.peek(pc++) + y)] = x;
                remaining_cycles = 3;
                break;

              case 0x98: // TYA
                a = y;
                flagN = a < 0;
                flagZ = a === 0;
                break;

              case 0x99: // STA Absolute Indexed Y
                this.poke((this.peek(pc++) & 0xFF) + ((this.peek(pc++) & 0xFF) << 8) + (y & 0xFF), a);
                remaining_cycles = 4;
                break;

              case 0x9A: // TXS
                s = x;
                break;

              case 0x9D: // STA Absolute Indexed X
                this.poke((this.peek(pc++) & 0xFF) + ((this.peek(pc++) & 0xFF) << 8) + (x & 0xFF), a);
                remaining_cycles = 4;
                break;

              case 0xA0:  // LDY Immediate
                y = this.peek(pc++);
                flagN = y < 0;
                flagZ = y === 0;
                break;

              case 0xA1: // LDA Indexed X Indirect
                address = 0xFF & (this.peek(pc++) + x);
                a = this.peek(this.ram[address] & 0xFF | (this.ram[address + 1] << 8) & 0xFF00);
                flagN = a < 0;
                flagZ = a === 0;
                remaining_cycles = 5;
                break;

              case 0xA2:  // LDX Immediate
                x = this.peek(pc++);
                flagN = x < 0;
                flagZ = x === 0;
                break;

              case 0xA4:  // LDY Zero Page
                y = this.ram[0xFF & this.peek(pc++)];
                flagN = y < 0;
                flagZ = y === 0;
                remaining_cycles = 2;
                break;

              case 0xA5: // LDA Zero page
                a = this.ram[0xFF & this.peek(pc++)];
                flagN = a < 0;
                flagZ = a === 0;
                remaining_cycles = 2;
                break;

              case 0xA6: // LDX Zero page
                x = this.ram[0xFF & this.peek(pc++)];
                flagN = x < 0;
                flagZ = x === 0;
                remaining_cycles = 2;
                break;

              case 0xA8:  // TAY
                y = a;
                flagN = y < 0;
                flagZ = y === 0;
                break;

              case 0xA9: // LDA Immediate
                a = this.peek(pc++);
                flagN = a < 0;
                flagZ = a === 0;
                break;

              case 0xAA:  // TAX
                x = a;
                flagN = x < 0;
                flagZ = x === 0;
                break;

              case 0xAC:  // LDY Absolute
                y = this.peek(this.peek(pc++) & 0xFF | (this.peek(pc++) << 8) & 0xFF00);
                flagN = y < 0;
                flagZ = y === 0;
                remaining_cycles = 3;
                break;

              case 0xAD: // LDA Absolute
                a = this.peek(this.peek(pc++) & 0xFF | (this.peek(pc++) << 8) & 0xFF00);
                flagN = a < 0;
                flagZ = a === 0;
                remaining_cycles = 3;
                break;

              case 0xAE: // STX Absolute
                x = this.peek(this.peek(pc++) & 0xFF | (this.peek(pc++) << 8) & 0xFF00);
                flagN = x < 0;
                flagZ = x === 0;
                remaining_cycles = 3;
                break;

              case 0xB0: // BCS
                if (flagC) {
                  pc += this.peek(pc);
                  remaining_cycles = 2;
                }
                pc++;
                break;

              case 0xB1: // LDA (),Y
                zp_address = 0xFF & this.peek(pc++);
                address = (this.ram[zp_address] & 0xFF) + (y & 0xFF);
                remaining_cycles = address > 255 ? 5 : 4;
                address += (this.ram[zp_address + 1] << 8) & 0xFF00;
                a = this.peek(address);
                flagN = a < 0;
                flagZ = a === 0;
                break;

              case 0xB4: // LDY Page Zero Indexed X
                y = this.ram[0xFF & (this.peek(pc++) + x)];
                flagN = y < 0;
                flagZ = y === 0;
                remaining_cycles = 3;
                break;

              case 0xB5: // LDA Page Zero Indexed X
                a = this.ram[0xFF & (this.peek(pc++) + x)];
                flagN = a < 0;
                flagZ = a === 0;
                remaining_cycles = 3;
                break;

              case 0xB6: // LDX Page Zero Indexed Y
                x = this.ram[0xFF & (this.peek(pc++) + y)];
                flagN = x < 0;
                flagZ = x === 0;
                remaining_cycles = 3;
                break;

              case 0xB8: // CLV
                flagV = false;
                break;

              case 0xB9: // LDA Absolute Indexed Y
                address = (this.peek(pc++) & 0xFF) + (y & 0xFF);
                remaining_cycles = address > 255 ? 4 : 3;
                address = ((this.peek(pc++) << 8) + address) & 0xFFFF;
                a = this.peek(address);
                flagN = a < 0;
                flagZ = a === 0;
                break;

              case 0xBA: // TSX
                x = s;
                flagN = x < 0;
                flagZ = x === 0;
                break;

              case 0xBC:  // LDY Absolute Indexed X
                address = (this.peek(pc++) & 0xFF) + (x & 0xFF);
                remaining_cycles = address > 255 ? 4 : 3;
                address = ((this.peek(pc++) << 8) + address) & 0xFFFF;
                y = this.peek(address);
                flagN = y < 0;
                flagZ = y === 0;
                break;

              case 0xBD: // LDA Absolute Indexed X
                address = (this.peek(pc++) & 0xFF) + (x & 0xFF);
                remaining_cycles = address > 255 ? 4 : 3;
                address = ((this.peek(pc++) << 8) + address) & 0xFFFF;
                a = this.peek(address);
                flagN = a < 0;
                flagZ = a === 0;
                break;

              case 0xBE: // LDX Absolute Indexed Y
                address = (this.peek(pc++) & 0xFF) + (y & 0xFF);
                remaining_cycles = address > 255 ? 4 : 3;
                address = ((this.peek(pc++) << 8) + address) & 0xFFFF;
                x = this.peek(address);
                flagN = x < 0;
                flagZ = x === 0;
                break;
            }
          } else // (opcode & 0xC0) != 0x80
          {
            let rmw = false;
            remaining_cycles = 0;

            switch (opcode & 0x1F) // addressing mode
            {
              case 0x01: // Indexed X Indirect
                zp_address = 0xFF & (this.peek(pc++) + x);
                address = this.ram[zp_address] & 0xFF | (this.ram[zp_address + 1] << 8) & 0xFF00;
                tmp = this.peek(address);
                remaining_cycles = 5;
                break;

              case 0x05: // Zero page
                tmp = this.ram[this.peek(pc++) & 0xFF];
                remaining_cycles = 2;
                break;

              case 0x06: // RMW Zero page
                address = this.peek(pc++) & 0xFF;
                tmp = this.ram[address];
                remaining_cycles = 4;
                rmw = true;
                break;

              case 0x09: // Immediate
                tmp = this.peek(pc++);
                remaining_cycles = 1;
                break;

              case 0x0D: // Absolute
                address = this.deek(pc);
                pc += 2;
                tmp = this.peek(address);
                remaining_cycles = 3;
                break;

              case 0x0E: // RMW Absolute
                address = this.deek(pc);
                pc += 2;
                tmp = this.peek(address);
                remaining_cycles = 5;
                rmw = true;
                break;

              case 0x11: // Indirect Indexed Y
                zp_address = 0xFF & this.peek(pc++);
                address = (this.ram[zp_address] & 0xFF | (this.ram[zp_address + 1] << 8) & 0xFF00) + (0xFF & y);
                tmp = this.peek(address);
                remaining_cycles = 4; // +1 if addition of Y changes page
                break;

              case 0x15: // Page Zero Indexed X
                tmp = this.ram[0xFF & (this.peek(pc++) + x)];
                remaining_cycles = 3;
                break;

              case 0x16: // RMW Page Zero Indexed X
                address = (this.peek(pc++) + x) & 0xFF;
                tmp = this.ram[address];
                remaining_cycles = 5;
                rmw = true;
                break;

              case 0x19: // Absolute Indexed Y
                address = (this.peek(pc++) & 0xFF) + (y & 0xFF);
                remaining_cycles = address > 255 ? 4 : 3;
                address = ((this.peek(pc++) << 8) + address) & 0xFFFF;
                tmp = this.peek(address);
                break;

              case 0x1E: // RMW Absolute Indexed X
                rmw = true;
              // tslint:disable-next-line:no-switch-case-fall-through
              case 0x1D: // Absolute Indexed X
                address = (this.peek(pc++) & 0xFF) + (x & 0xFF);
                remaining_cycles = address > 255 ? 4 : 3;
                address = ((this.peek(pc++) << 8) + address) & 0xFFFF;
                tmp = this.peek(address);
                break;
            }

            if (remaining_cycles === 0) // not a regular instruction with addressing mode
            {
              remaining_cycles = 1;

              switch (opcode & 0xFF) {
                case 0x00:  // BRK
                  tmp = 0x30;
                  if (flagN) {
                    tmp += 128;
                  }
                  if (flagV) {
                    tmp += 64;
                  }
                  if (flagD) {
                    tmp += 8;
                  }
                  if (flagI) {
                    tmp += 4;
                  }
                  if (flagZ) {
                    tmp += 2;
                  }
                  if (flagC) {
                    tmp += 1;
                  }

                  this.ram[0x100 + (0xFF & s--)] = ((pc + 1) >> 8);
                  this.ram[0x100 + (0xFF & s--)] = (pc + 1);
                  this.ram[0x100 + (0xFF & s--)] = tmp;
                  flagI = true;
                  pc = this.deek(0xFFFE);
                  remaining_cycles = 6;
                  break;

                case 0x20:  // JSR
                  pc++;
                  this.ram[0x100 + (0xFF & s--)] = (pc >> 8);
                  this.ram[0x100 + (0xFF & s--)] = pc;
                  pc = this.deek(pc - 1);
                  break;

                case 0x40:  // RTI
                  tmp = this.ram[0x100 + (0xFF & (++s))];
                  flagN = tmp < 0;
                  flagV = (tmp & 64) !== 0;
                  flagD = (tmp & 8) !== 0;
                  flagI = (tmp & 4) !== 0;
                  flagZ = (tmp & 2) !== 0;
                  flagC = (tmp & 1) !== 0;
                  pc = 0xFF & this.ram[0x100 + (0xFF & (++s))];
                  pc += 0xFF00 & (this.ram[0x100 + (0xFF & (++s))] << 8);
                  remaining_cycles = 5;
                  break;

                case 0x60:  // RTS
                  pc = 0xFF & this.ram[0x100 + (0xFF & (++s))];
                  pc += 0xFF00 & (this.ram[0x100 + (0xFF & (++s))] << 8);
                  pc++;
                  remaining_cycles = 5;
                  break;

                case 0xC0:  // CPY Immediate
                  tmp = this.peek(pc++);
                  flagC = (0xFF & tmp) <= (0xFF & y);
                  flagN = (y - tmp) < 0;
                  flagZ = y === tmp;
                  break;

                case 0xE0:  // CPX Immediate
                  tmp = this.peek(pc++);
                  flagN = (x - tmp) < 0;
                  flagZ = x === tmp;
                  flagC = (0xFF & tmp) <= (0xFF & x);
                  break;

                case 0x24:  // BIT Zero Page
                  tmp = this.ram[0xFF & this.peek(pc++)];
                  flagZ = (a & tmp) === 0;
                  flagN = (tmp & 0x80) !== 0;
                  flagV = (tmp & 0x40) !== 0;
                  remaining_cycles = 2;
                  break;

                case 0xC4:  // CPY Zero Page
                  tmp = this.ram[0xFF & this.peek(pc++)];
                  flagC = (0xFF & tmp) <= (0xFF & y);
                  flagN = (y - tmp) < 0;
                  flagZ = y === tmp;
                  remaining_cycles = 2;
                  break;

                case 0xE4:  // CPX Zero Page
                  tmp = this.ram[0xFF & this.peek(pc++)];
                  flagC = (0xFF & tmp) <= (0xFF & x);
                  flagN = (x - tmp) < 0;
                  flagZ = x === tmp;
                  remaining_cycles = 2;
                  break;

                case 0x08:  // PHP
                  tmp = 0x30;
                  if (flagN) {
                    tmp += 128;
                  }
                  if (flagV) {
                    tmp += 64;
                  }
                  if (flagD) {
                    tmp += 8;
                  }
                  if (flagI) {
                    tmp += 4;
                  }
                  if (flagZ) {
                    tmp += 2;
                  }
                  if (flagC) {
                    tmp += 1;
                  }
                  this.ram[0x100 + (0xFF & s--)] = tmp;
                  remaining_cycles = 2;
                  break;

                case 0x28:  // PLP
                  tmp = this.ram[0x100 + (0xFF & (++s))];
                  flagN = tmp < 0;
                  flagV = (tmp & 64) !== 0;
                  flagD = (tmp & 8) !== 0;
                  flagI = (tmp & 4) !== 0;
                  flagZ = (tmp & 2) !== 0;
                  flagC = (tmp & 1) !== 0;
                  remaining_cycles = 3;
                  break;

                case 0x48:  // PHA
                  this.ram[0x100 + (0xFF & s--)] = a;
                  remaining_cycles = 2;
                  break;

                case 0x68:  // PLA
                  a = this.ram[0x100 + (0xFF & (++s))];
                  flagN = a < 0;
                  flagZ = a === 0;
                  remaining_cycles = 3;
                  break;

                case 0xC8:  // INY
                  y++;
                  flagN = y < 0;
                  flagZ = y === 0;
                  break;

                case 0xE8:  // INX
                  x++;
                  flagN = x < 0;
                  flagZ = x === 0;
                  break;

                case 0x0A:  // ASL A
                  flagC = a < 0;
                  a <<= 1;
                  flagN = a < 0;
                  flagZ = a === 0;
                  break;

                case 0x2A:  // ROL A
                  tmp = (a << 1);
                  if (flagC) {
                    tmp |= 1;
                  }
                  flagC = a < 0;
                  flagN = tmp < 0;
                  flagZ = tmp === 0;
                  a = tmp;
                  break;

                case 0x4A:  // LSR A
                  flagC = (a & 1) !== 0;
                  a = ((a & 0xFF) >> 1);
                  flagN = false;
                  flagZ = a === 0;
                  break;

                case 0x6A:  // ROR A
                  tmp = ((a & 0xFF) >> 1);
                  if (flagC) {
                    tmp |= 0x80;
                  }
                  flagC = (a & 1) !== 0;
                  flagN = tmp < 0;
                  flagZ = tmp === 0;
                  a = tmp;
                  break;

                case 0xCA:  // DEX
                  x--;
                  flagN = x < 0;
                  flagZ = x === 0;
                  break;

                case 0xEA:  // NOP
                  break;

                case 0x2C:  // BIT Absolute
                  tmp = this.peek(this.peek(pc++) & 0xFF | (this.peek(pc++) << 8) & 0xFF00);
                  flagZ = (a & tmp) === 0;
                  flagN = (tmp & 0x80) !== 0;
                  flagV = (tmp & 0x40) !== 0;
                  remaining_cycles = 3;
                  break;

                case 0x4C:  // JMP Absolute
                  pc = this.deek(pc);
                  remaining_cycles = 2;
                  break;

                case 0x6C:  // JMP Absolute Indirect
                  const address_low = this.peek(pc);
                  address = (this.peek(pc + 1) << 8) & 0xFF00;
                  pc = this.peek(address + (address_low & 0xFF)) & 0xFF;
                  pc += (this.peek(address + ((address_low + 1) & 0xFF)) << 8) & 0xFF00;
                  remaining_cycles = 4;
                  break;

                case 0xCC:  // CPY Absolute
                  address = this.peek(pc++) & 0xFF | (this.peek(pc++) << 8) & 0xFF00;
                  tmp = this.peek(address);
                  flagC = (0xFF & tmp) <= (0xFF & y);
                  flagN = (y - tmp) < 0;
                  flagZ = y === tmp;
                  remaining_cycles = 3;
                  break;

                case 0xEC:  // CPX Absolute
                  address = this.peek(pc++) & 0xFF | (this.peek(pc++) << 8) & 0xFF00;
                  tmp = this.peek(address);
                  flagN = (x - tmp) < 0;
                  flagZ = x === tmp;
                  flagC = (0xFF & tmp) <= (0xFF & x);
                  remaining_cycles = 3;
                  break;

                case 0x10:  // BPL
                  if (!flagN) {
                    remaining_cycles = 2;
                    const peeked = this.peek(pc);
                    pc += peeked > 127 ? peeked - 256 : peeked;
                    // pc += this.peek(pc);
                  }
                  pc++;
                  break;

                case 0x30:  // BMI
                  if (flagN) {
                    // pc += this.peek(pc);
                    const peeked = this.peek(pc);
                    pc += peeked > 127 ? peeked - 256 : peeked;
                    remaining_cycles = 2;
                  }
                  pc++;
                  break;

                case 0x50:  // BVC
                  if (!flagV) {
                    // pc += this.peek(pc);
                    const peeked = this.peek(pc);
                    pc += peeked > 127 ? peeked - 256 : peeked;
                    remaining_cycles = 2;
                  }
                  pc++;
                  break;

                case 0x70:  // BVS
                  if (flagV) {
                    // pc += this.peek(pc);
                    const peeked = this.peek(pc);
                    pc += peeked > 127 ? peeked - 256 : peeked;
                    remaining_cycles = 2;
                  }
                  pc++;
                  break;

                case 0xD0:  // BNE
                  if (!flagZ) {
                    // pc += this.peek(pc);
                    const peeked = this.peek(pc);
                    pc += peeked > 127 ? peeked - 256 : peeked;
                    remaining_cycles = 2;
                  }
                  pc++;
                  break;

                case 0xF0:  // BEQ
                  if (flagZ) {
                    // pc += this.peek(pc);
                    const peeked = this.peek(pc);
                    pc += peeked > 127 ? peeked - 256 : peeked;
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
                  pc--;
                  this.registerPC = pc;
                  this.registerA = a;
                  this.registerX = x;
                  this.registerY = y;
                  this.registerP = 0x30;
                  if (flagI) {
                    this.registerP |= 4;
                  }
                  this.emulatorTrap(pc);
                  a = this.registerA;
                  x = this.registerX;
                  y = this.registerY;
                  pc = this.registerPC;
                  flagN = this.registerP < 0;
                  flagV = (this.registerP & 64) !== 0;
                  flagD = (this.registerP & 8) !== 0;
                  flagI = (this.registerP & 4) !== 0;
                  flagZ = (this.registerP & 2) !== 0;
                  flagC = (this.registerP & 1) !== 0;
                  break;
              }
            } else if (rmw) {
              switch (opcode & 0xE0) {
                case 0x00: // ASL
                  flagC = tmp < 0;
                  tmp2 = (tmp << 1);
                  break;

                case 0x20: // ROL
                  tmp2 = (tmp << 1);
                  if (flagC) {
                    tmp2 |= 1;
                  }
                  flagC = tmp < 0;
                  break;

                case 0x40: // LSR
                  flagC = (tmp & 1) !== 0;
                  tmp2 = ((tmp & 0xFF) >> 1);
                  break;

                case 0x60: // ROR
                  tmp2 = ((tmp & 0xFF) >> 1);
                  if (flagC) {
                    tmp2 |= 0x80;
                  }
                  flagC = (tmp & 1) !== 0;
                  break;

                case 0xC0: // DEC
                  tmp2 = (tmp - 1);
                  break;

                case 0xE0: // INC
                  tmp2 = (tmp + 1);
                  break;
              }

              flagN = tmp2 < 0;
              flagZ = tmp2 === 0;

              switch (opcode & 0x1F) {
                case 0x06:
                case 0x16:
                  this.ram[address] = tmp2;
                  break;

                case 0x0E:
                case 0x1E:
                  this.poke(address, tmp2);
                  break;
              }
            } else // !rmw
            {
              switch (opcode & 0xE0) {
                case 0x00: // ORA
                  a |= tmp;
                  flagN = a < 0;
                  flagZ = a === 0;
                  break;

                case 0x20: // AND
                  a &= tmp;
                  flagN = a < 0;
                  flagZ = a === 0;
                  break;

                case 0x40: // EOR
                  a ^= tmp;
                  flagN = a < 0;
                  flagZ = a === 0;
                  break;

                case 0x60: // ADC
                  if (flagD) {
                    const c = flagC ? 1 : 0;
                    k = (a & 0xF) + (tmp & 0xF) + c;

                    if ((k & 0xFF) >= 0xA) {
                      k = (k & 0xFF00) + ((k + 0x6) & 0xFF);
                    }
                    if ((k & 0xFF) >= 0x20) {
                      k = (k & 0xFF0F) + 0x10;
                    }

                    k += (a & 0xF0) + (tmp & 0xF0);

                    flagZ = (0xFF & (a + tmp + c)) === 0;
                    flagV = (~((0xFF & a) ^ (0xFF & tmp)) & ((0xFF & a) ^ (0xFF & k)) & 0x80) !== 0;
                    flagC = false;
                    if (k >= 0xA0) {
                      k += 0x60;
                      flagC = true;
                    }

                    a = k;
                    flagN = a < 0;
                  } else {
                    k = (0xFF & a) + (0xFF & tmp) + (flagC ? 1 : 0);
                    flagV = (~((0xFF & a) ^ (0xFF & tmp)) & ((0xFF & a) ^ (0xFF & k)) & 0x80) !== 0;
                    flagC = (k & 0xFF00) !== 0;
                    a = k;
                    flagZ = a === 0;
                    flagN = a < 0;
                  }
                  break;

                case 0xC0: // CMP
                  flagN = (a - tmp) < 0;
                  flagZ = a === tmp;
                  flagC = (0xFF & tmp) <= (0xFF & a);
                  break;

                case 0xE0: // SBC
                  if (flagD) {
                    const c = flagC ? 0 : 1;
                    k = (a & 0xF) + 0x100;
                    k -= (tmp & 0xF) + c;
                    if (k < 0x100) {
                      k -= 6;
                    }
                    if (k < 0xF0) {
                      k += 0x10;
                    }
                    k += a & 0xF0;
                    k -= tmp & 0xF0;

                    flagC = true;
                    flagN = (k & 0x80) !== 0;
                    flagZ = (a - tmp - c === 0);
                    flagV = (~((0xFF & k) ^ tmp) & (a ^ tmp) & 0x80) !== 0;
                    if ((k & 0xFF00) === 0) {
                      k = (k & 0xFF00) + (0xFF & ((k & 0xFF) - 0x60));
                      flagC = false;
                    }
                    a = k;
                  } else {
                    k = 0x100 + (0xFF & a) - (0xFF & tmp) - (flagC ? 0 : 1);
                    flagV = (~((0xFF & k) ^ tmp) & (a ^ tmp) & 0x80) !== 0;
                    flagC = (k & 0xFF00) !== 0;
                    a = k;
                    flagN = a < 0;
                    flagZ = a === 0;
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


        if (this.viaT1overflow) {
          if (this.viaT1running) {
            this.viaT1running = (this.viaACR & 0x40) !== 0;
            this.viaIFR |= 0x40;
            this.viaIRQ = (this.viaIER & this.viaIFR) !== 0;
          }

          this.viaT1C = this.viaT1L;
          this.viaT1overflow = false;

          // TO-DO: PB7 control
        } else {
          this.viaT1C--;
          if (this.viaT1C < 0) {
            this.viaT1overflow = true;
          }
        }

        if ((this.viaACR & 0x20) === 0) {
          if (this.viaT2overflow && this.viaT2running) {
            this.viaT2running = false;
            this.viaIFR |= 0x20;
            this.viaIRQ = (this.viaIER & this.viaIFR) !== 0;
          }

          this.viaT2C--;
          this.viaT2overflow = (this.viaT2C === -1);
        }

// TO-DO: SR operation
// TO-DO: handshaking pulse mode


      }
    }
    this.registerPC = pc;
    this.registerA = a;
    this.registerX = x;
    this.registerY = y;
    this.registerS = s;
    this.registerP = 0x30;
    if (flagN) {
      this.registerP |= 0x80;
    }
    if (flagV) {
      this.registerP |= 0x40;
    }
    if (flagD) {
      this.registerP |= 8;
    }
    if (flagI) {
      this.registerP |= 4;
    }
    if (flagZ) {
      this.registerP |= 2;
    }
    if (flagC) {
      this.registerP |= 1;
    }
  }

  private updatePSG(): void {
    if (this.EMULATE_SOUND) {
      this.psgACounter++;
      if (this.psgACounter >= this.psgAPeriod) {
        // output is inversed every period,
        // thus A_period is in fact half the period of the tone
        this.psgAOutput = ~this.psgAOutput;
        this.psgACounter = 0;
      }

      this.psgBCounter++;
      if (this.psgBCounter >= this.psgBPeriod) {
        this.psgBOutput = ~this.psgBOutput;
        this.psgBCounter = 0;
      }

      this.psgCCounter++;
      if (this.psgCCounter >= this.psgCPeriod) {
        this.psgCOutput = ~this.psgCOutput;
        this.psgCCounter = 0;
      }

      this.psgNCounter++;
      if (this.psgNCounter >= this.psgNPeriod) {
        this.psgNOutput = (this.psgNShifter & 1) !== 0 ? -1 : 0;
        this.psgNCounter = 0;
        this.psgNShifter >>= 1;
        if (((this.psgNShifter ^ this.psgNOutput) & 2) !== 0) {
          this.psgNShifter |= 0x10000;
        }
      }

      // computing envelop amplitude, and thus each chanel's amplitude
      this.psgECounter++;
      if (this.psgECounter >= this.psgEPeriod) {
        let amplitude = this.psgEAmplitude;
        this.psgECounter = 0;
        if (this.psgECountUp) {
          amplitude++;
        }
        if (this.psgECountDown) {
          amplitude--;
        }
        if (amplitude === 16 || amplitude === -1) {
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
          if (!this.psgEContinue) {
            this.psgECountUp = this.psgECountDown = false;
            this.psgEAmplitude = 0;
          } else if (this.psgEHold) {
            this.psgECountUp = this.psgECountDown = false;
            if (this.psgEAlternate) {
              this.psgEAmplitude = this.psgEAttack ? 0 : 15;
            } else {
              this.psgEAmplitude = this.psgEAttack ? 15 : 0;
            }
          } else if (this.psgEAlternate) {
            this.psgECountUp = !this.psgECountUp;
            this.psgECountDown = !this.psgECountDown;
            this.psgEAmplitude = this.psgECountUp ? 0 : 15;
          } else {
            this.psgEAmplitude = amplitude & 15;
          }
        } else {
          this.psgEAmplitude = amplitude;
        }

        if (this.psgAEnvelop) {
          this.psgAAmplitude = this.psgEAmplitude;
        }
        if (this.psgBEnvelop) {
          this.psgBAmplitude = this.psgEAmplitude;
        }
        if (this.psgCEnvelop) {
          this.psgCAmplitude = this.psgEAmplitude;
        }
      }

      // mixing noise and tone generators, and applying amplitudes to compute the 3 chanels
      this.psgAChanel[this.psgOutIndex] =
        this.volume[(this.psgAOutput | this.psgADisabled) & (this.psgNOutput | this.psgNADisabled) & this.psgAAmplitude];
      this.psgBChanel[this.psgOutIndex] =
        this.volume[(this.psgBOutput | this.psgBDisabled) & (this.psgNOutput | this.psgNBDisabled) & this.psgBAmplitude];
      this.psgCChanel[this.psgOutIndex] =
        this.volume[(this.psgCOutput | this.psgCDisabled) & (this.psgNOutput | this.psgNCDisabled) & this.psgCAmplitude];
      this.psgOutIndex++;
    }
  }

  private emulatorTrap(pc: number): void {

  }
}
