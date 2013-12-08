/**
 * Copyright (C) 2012 Iordan Iordanov
 * Copyright (C) 2001-2004 HorizonLive.com, Inc.  All Rights Reserved.
 * Copyright (C) 2001-2006 Constantin Kaplinsky.  All Rights Reserved.
 * Copyright (C) 2000 Tridia Corporation.  All Rights Reserved.
 * Copyright (C) 1999 AT&T Laboratories Cambridge.  All Rights Reserved.
 * 
 * This is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this software; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307,
 * USA.
 */

package com.iiordanov.bVNC;

import java.io.*;
//- import java.awt.*;
//- import java.awt.event.*;
import java.net.Socket;
//- import java.util.zip.*;
import android.util.Log;
import android.widget.Toast;

import com.iiordanov.bVNC.DH;
import com.iiordanov.bVNC.input.RemoteKeyboard;

/**
 * Access the RFB protocol through a socket.
 * <p>
 * This class has no knowledge of the android-specific UI; it sees framebuffer updates
 * and input events as defined in the RFB protocol.
 * 
 */
class RfbProto implements RfbConnectable {

    final static String TAG = "RfbProto";
    
  final static String
    versionMsg_3_3 = "RFB 003.003\n",
    versionMsg_3_7 = "RFB 003.007\n",
    versionMsg_3_8 = "RFB 003.008\n";

  // Vendor signatures: standard VNC/RealVNC, TridiaVNC, and TightVNC
  final static String
    StandardVendor  = "STDV",
    TridiaVncVendor = "TRDV",
    TightVncVendor  = "TGHT";

  // Security types
  final static int
    SecTypeInvalid  = 0,
    SecTypeNone     = 1,
    SecTypeVncAuth  = 2,
    SecTypeTight    = 16,
    SecTypeTLS      = 18,
    SecTypeVeNCrypt = 19,
    SecTypeUltra34 = 0xfffffffa;
  
  /* VeNCrypt subtypes */
  public static final int secTypePlain     = 256;
  public static final int secTypeTLSNone   = 257;
  public static final int secTypeTLSVnc    = 258;
  public static final int secTypeTLSPlain  = 259;
  public static final int secTypeX509None  = 260;
  public static final int secTypeX509Vnc   = 261;
  public static final int secTypeX509Plain = 262;
  public static final int secTypeIdent     = 265;
  public static final int secTypeTLSIdent  = 266;
  public static final int secTypeX509Ident = 267;

  // Supported tunneling types
  final static int
    NoTunneling = 0;
  final static String
    SigNoTunneling = "NOTUNNEL";

  // Supported authentication types
  final static int
    AuthNone      = 1,
    AuthVNC       = 2,
    AuthUnixLogin = 129,
    AuthUltra      = 17;
  final static String
    SigAuthNone      = "NOAUTH__",
    SigAuthVNC       = "VNCAUTH_",
    SigAuthUnixLogin = "ULGNAUTH";

  // VNC authentication results
  final static int
    VncAuthOK      = 0,
    VncAuthFailed  = 1,
    VncAuthTooMany = 2;

  // Server-to-client messages
  final static int
    FramebufferUpdate   = 0,
    SetColourMapEntries = 1,
    Bell                = 2,
    ServerCutText       = 3,
    TextChat             = 11;

  // Client-to-server messages
  final static int
    SetPixelFormat           = 0,
    FixColourMapEntries      = 1,
    SetEncodings             = 2,
    FramebufferUpdateRequest = 3,
    KeyboardEvent            = 4,
    PointerEvent             = 5,
    ClientCutText            = 6;

  // Supported encodings and pseudo-encodings
  final static int
    EncodingRaw            = 0,
    EncodingCopyRect       = 1,
    EncodingRRE            = 2,
    EncodingCoRRE          = 4,
    EncodingHextile        = 5,
    EncodingZlib           = 6,
    EncodingTight          = 7,
    EncodingZRLE           = 16,
    EncodingCompressLevel0 = 0xFFFFFF00,
    EncodingQualityLevel0  = 0xFFFFFFE0,
    EncodingXCursor        = 0xFFFFFF10,
    EncodingRichCursor     = 0xFFFFFF11,
    EncodingPointerPos     = 0xFFFFFF18,
    EncodingLastRect       = 0xFFFFFF20,
    EncodingNewFBSize      = 0xFFFFFF21;
  final static String
    SigEncodingRaw            = "RAW_____",
    SigEncodingCopyRect       = "COPYRECT",
    SigEncodingRRE            = "RRE_____",
    SigEncodingCoRRE          = "CORRE___",
    SigEncodingHextile        = "HEXTILE_",
    SigEncodingZlib           = "ZLIB____",
    SigEncodingTight          = "TIGHT___",
    SigEncodingZRLE           = "ZRLE____",
    SigEncodingCompressLevel0 = "COMPRLVL",
    SigEncodingQualityLevel0  = "JPEGQLVL",
    SigEncodingXCursor        = "X11CURSR",
    SigEncodingRichCursor     = "RCHCURSR",
    SigEncodingPointerPos     = "POINTPOS",
    SigEncodingLastRect       = "LASTRECT",
    SigEncodingNewFBSize      = "NEWFBSIZ";

  final static int MaxNormalEncoding = 255;

  // Contstants used in the Hextile decoder
  final static int
    HextileRaw                 = 1,
    HextileBackgroundSpecified = 2,
    HextileForegroundSpecified = 4,
    HextileAnySubrects         = 8,
    HextileSubrectsColoured    = 16;

  // Contstants used in the Tight decoder
  final static int TightMinToCompress = 12;
  final static int
    TightExplicitFilter = 0x04,
    TightFill           = 0x08,
    TightJpeg           = 0x09,
    TightMaxSubencoding = 0x09,
    TightFilterCopy     = 0x00,
    TightFilterPalette  = 0x01,
    TightFilterGradient = 0x02;

  // Constants used for UltraVNC chat extension
  final static int
      CHAT_OPEN = -1,
    CHAT_CLOSE = -2,
    CHAT_FINISHED = -3;

  String host;
  int port;
  Socket sock;
  DataInputStream is;
  OutputStream os;
  
  DH dh;
  long dh_resp;
  
  //- SessionRecorder rec;
  boolean inNormalProtocol = false;
  //- VncViewer viewer;

  // Java on UNIX does not call keyPressed() on some keys, for example
  // swedish keys To prevent our workaround to produce duplicate
  // keypresses on JVMs that actually works, keep track of if
  // keyPressed() for a "broken" key was called or not. 
  boolean brokenKeyPressed = false;

  // This will be set to true on the first framebuffer update
  // containing Zlib-, ZRLE- or Tight-encoded data.
  //boolean wereZlibUpdates = false;

  // This will be set to false if the startSession() was called after
  // we have received at least one Zlib-, ZRLE- or Tight-encoded
  // framebuffer update.
  boolean recordFromBeginning = true;

  // This fields are needed to show warnings about inefficiently saved
  // sessions only once per each saved session file.
  boolean zlibWarningShown;
  boolean tightWarningShown;

  // Before starting to record each saved session, we set this field
  // to 0, and increment on each framebuffer update. We don't flush
  // the SessionRecorder data into the file before the second update. 
  // This allows us to write initial framebuffer update with zero
  // timestamp, to let the player show initial desktop before
  // playback.
  //int numUpdatesInSession;

  // Measuring network throughput.
  boolean timing;
  long timeWaitedIn100us;
  long timedKbits;

  // Protocol version and TightVNC-specific protocol options.
  int serverMajor, serverMinor;
  int clientMajor, clientMinor;
  boolean protocolTightVNC;
  CapsContainer tunnelCaps, authCaps;
  CapsContainer serverMsgCaps, clientMsgCaps;
  CapsContainer encodingCaps;

  // If true, informs that the RFB socket was closed.
  private boolean closed;

  // The main processing loop continues while this is set to true;
  private boolean maintainConnection = true;

    // VNC Encoding parameters

  // Tight encoding parameters
    private int compressLevel = 8;
    private int jpegQuality = 6;

    // Used to determine if encoding update is necessary
    private int[] encodingsSaved = null;
    private int nEncodingsSaved = 0;
  
    // Handle for decoder object
    private Decoder decoder;
    
    // Suggests to the server whether the desktop should be shared or not
    private int shareDesktop = 1;

    // Suggests to the server a preferred encoding
    private int preferredEncoding = EncodingTight;
    
    // View Only mode
    private boolean viewOnly = false;
    
  //
  // Constructor. Make TCP connection to RFB server.
  //
  RfbProto(Decoder d, String h, int p, int prefEnc, boolean viewOnly) throws Exception {
    this.viewOnly = viewOnly;
    host = h;
    port = p;
    preferredEncoding = prefEnc;

       sock = new Socket(host, port);
    sock.setTcpNoDelay(true);
    // After much testing, 8192 does seem like the best compromize between
    // responsiveness and throughput.
    is = new DataInputStream(new BufferedInputStream(sock.getInputStream(), 8192));
    os = sock.getOutputStream();

    decoder = d;

    timing = false;
    timeWaitedIn100us = 5;
    timedKbits = 0;
  }
 

  public synchronized void closeSocket() {
      inNormalProtocol = false;
    try {
      sock.close();
      closed = true;
      //- System.out.println("RFB socket closed");
      Log.v(TAG, "RFB socket closed");
      /*-
      if (rec != null) {
    rec.close();
    rec = null;
      
      } */
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void close() {
      inNormalProtocol = false;
      maintainConnection = false;
      closeSocket();
  }

  
  synchronized boolean closed() {
    return closed;
  }

  public void initializeAndAuthenticate(String us, String pw, boolean useRepeater, String repeaterID, boolean anonTLS) throws Exception {
        
        // <RepeaterMagic>
        if (useRepeater && repeaterID != null && repeaterID.length()>0) {
            Log.i(TAG, "Negotiating repeater/proxy connection");
            byte[] protocolMsg = new byte[12];
            is.read(protocolMsg);
            byte[] buffer = new byte[250];
            System.arraycopy(repeaterID.getBytes(), 0, buffer, 0, repeaterID.length());
            os.write(buffer);
        }
        // </RepeaterMagic>

        readVersionMsg();
        Log.i(TAG, "RFB server supports protocol version " + serverMajor + "." + serverMinor);

        writeVersionMsg();
        Log.i(TAG, "Using RFB protocol version " + clientMajor + "." + clientMinor);

        int bitPref=0;
        if(us.length()>0)
          bitPref|=1;
        Log.d("debug","bitPref="+bitPref);
        int secType = negotiateSecurity(bitPref, anonTLS);
        int authType;
        if (secType == RfbProto.SecTypeTight) {
            initCapabilities();
            setupTunneling();
            authType = negotiateAuthenticationTight();
        } else if (secType == RfbProto.SecTypeUltra34) {
            prepareDH();
            authType = RfbProto.AuthUltra;
        } else if (secType == RfbProto.SecTypeTLS) {
            performTLSHandshake ();
            authType = negotiateSecurity(bitPref, false);
        //} else if (secType == RfbProto.SecTypeVeNCrypt) {
        //    rfb.performTLSHandshake ();
        //    authType = rfb.selectVeNCryptSecurityType();
        } else {
            authType = secType;
        }

        switch (authType) {
        case RfbProto.AuthNone:
            Log.i(TAG, "No authentication needed");
            authenticateNone();
            break;
        case RfbProto.AuthVNC:
            Log.i(TAG, "VNC authentication needed");
            authenticateVNC(pw);
            break;
        case RfbProto.AuthUltra:
            authenticateDH(us,pw);
            break;
        default:
            throw new Exception("Unknown authentication scheme " + authType);
        }
  }
  //
  // Read server's protocol version message
  //

  void readVersionMsg() throws Exception {

    byte[] b = new byte[12];

    readFully(b);

    if ((b[0] != 'R') || (b[1] != 'F') || (b[2] != 'B') || (b[3] != ' ')
    || (b[4] < '0') || (b[4] > '9') || (b[5] < '0') || (b[5] > '9')
    || (b[6] < '0') || (b[6] > '9') || (b[7] != '.')
    || (b[8] < '0') || (b[8] > '9') || (b[9] < '0') || (b[9] > '9')
    || (b[10] < '0') || (b[10] > '9') || (b[11] != '\n'))
    {
      Log.i(TAG,new String(b));
      throw new Exception("Host " + host + " port " + port +
              " is not an RFB server");
    }

    serverMajor = (b[4] - '0') * 100 + (b[5] - '0') * 10 + (b[6] - '0');
    serverMinor = (b[8] - '0') * 100 + (b[9] - '0') * 10 + (b[10] - '0');

    if (serverMajor < 3) {
      throw new Exception("RFB server does not support protocol version 3");
    }
  }


  //
  // Write our protocol version message
  //

  synchronized void writeVersionMsg() throws IOException {
    clientMajor = 3;
    if (serverMajor > 3 || serverMinor >= 8) {
      clientMinor = 8;
      os.write(versionMsg_3_8.getBytes());
    } else if (serverMinor >= 7) {
      clientMinor = 7;
      os.write(versionMsg_3_7.getBytes());
    } else {
      clientMinor = 3;
      os.write(versionMsg_3_3.getBytes());
    }
    protocolTightVNC = false;
  }


  //
  // Negotiate the authentication scheme.
  //

  int negotiateSecurity(int bitPref, boolean anontls) throws Exception {
    return (clientMinor >= 7) ?
      selectSecurityType(bitPref, anontls) : readSecurityType(bitPref);
  }

  //
  // Read security type from the server (protocol version 3.3).
  //

  int readSecurityType(int bitPref) throws Exception {
    int secType = is.readInt();

    switch (secType) {
    case SecTypeInvalid:
      readConnFailedReason();
      return SecTypeInvalid;    // should never be executed
    case SecTypeNone:
    case SecTypeVncAuth:
      return secType;
    case SecTypeUltra34:
      if((bitPref & 1) == 1)
        return secType;
      throw new Exception("Username required.");
    default:
      throw new Exception("Unknown security type from RFB server: " + secType);
    }
  }

  //
  // Select security type from the server's list (protocol versions 3.7/3.8).
  //

  int selectSecurityType(int bitPref, boolean anontls) throws Exception {
        int secType = SecTypeInvalid;

        // Read the list of security types.
        int nSecTypes = is.readUnsignedByte();
        if (nSecTypes == 0) {
          readConnFailedReason();
          return SecTypeInvalid;    // should never be executed
        }
        byte[] secTypes = new byte[nSecTypes];
        readFully(secTypes);

        Log.d(TAG, "There are: " +nSecTypes +" security types.");
        
        // Find out if the server supports TightVNC protocol extensions
        for (int i = 0; i < nSecTypes; i++) {
          Log.d(TAG, "Detected security type: " + secTypes[i]);

          if (secTypes[i] == SecTypeTight) {
            protocolTightVNC = true;
            os.write(SecTypeTight);
            return SecTypeTight;
          }
        }

        // Find first supported security type.
        for (int i = 0; i < nSecTypes; i++) {
          // If anontls is enforced, then only accept VNC over TLS. Otherwise, accept it and all others.
          if (anontls) {
              if (secTypes[i] == SecTypeTLS) {
                  secType = secTypes[i];
                  break;
              }
          } else {
              // If anontls is not enforced still accept it.
              if (secTypes[i] == SecTypeTLS)
                  secType = secTypes[i];
              
              // However, override it by the other security modes if they show up as well.
              if (secTypes[i] == SecTypeNone || secTypes[i] == SecTypeVncAuth) {
                  secType = secTypes[i];
                    break;
              }             
          }
        }

        if (secType == SecTypeInvalid) {
          throw new Exception("Server did not offer supported security type. " +
                               "Please ensure you have picked the right item in Connection Type.");
        } else {
          os.write(secType);
        }

        return secType;
  }

  int selectVeNCryptSecurityType() throws Exception {
        int secType = SecTypeInvalid;

        // Read the list of security types.
        int nSecTypes = is.readUnsignedByte();
        if (nSecTypes == 0) {
          readConnFailedReason();
          return SecTypeInvalid;    // should never be executed
        }
        byte[] secTypes = new byte[nSecTypes];
        readFully(secTypes);

        Log.d(TAG, "There are: " +nSecTypes +" security types.");

        // Find first supported security type.
        for (int i = 0; i < nSecTypes; i++) {
          if (secTypes[i] == secTypeX509Plain ||
              secTypes[i] == secTypeX509Vnc   ||
              secTypes[i] == secTypeX509Ident ||
              secTypes[i] == secTypeX509None  ||
              secTypes[i] == secTypeTLSPlain  ||
              secTypes[i] == secTypeTLSVnc    ||
              secTypes[i] == secTypeTLSIdent  ||
              secTypes[i] == secTypeTLSNone   ||
              secTypes[i] == secTypeIdent     ||
              secTypes[i] == secTypePlain ) {
            secType = secTypes[i];
            break;
          }
        }

        if (secType == SecTypeInvalid) {
          throw new Exception("Server did not offer supported security type");
        } else {
          os.write(secType);
        }

        return secType;
      }

  //
  // Perform "no authentication".
  //

  void authenticateNone() throws Exception {
    if (clientMinor >= 8)
      readSecurityResult("No authentication");
  }

  //
  // Perform standard VNC Authentication.
  //

  void authenticateVNC(String pw) throws Exception {
    byte[] challenge = new byte[16];
    readFully(challenge);

    if (pw.length() > 8)
      pw = pw.substring(0, 8);    // Truncate to 8 chars

    // Truncate password on the first zero byte.
    int firstZero = pw.indexOf(0);
    if (firstZero != -1)
      pw = pw.substring(0, firstZero);

    byte[] key = {0, 0, 0, 0, 0, 0, 0, 0};
    System.arraycopy(pw.getBytes(), 0, key, 0, pw.length());

    DesCipher des = new DesCipher(key);

    des.encrypt(challenge, 0, challenge, 0);
    des.encrypt(challenge, 8, challenge, 8);

    os.write(challenge);

    readSecurityResult("VNC authentication");
  }

  //
  // Read security result.
  // Throws an exception on authentication failure.
  //

  void readSecurityResult(String authType) throws Exception {
    int securityResult = is.readInt();

    switch (securityResult) {
    case VncAuthOK:
      System.out.println(authType + ": success");
      break;
    case VncAuthFailed:
      if (clientMinor >= 8)
        readConnFailedReason();
      throw new Exception(authType + ": failed");
    case VncAuthTooMany:
      throw new Exception(authType + ": failed, too many tries");
    default:
      throw new Exception(authType + ": unknown result " + securityResult);
    }
  }

  //
  // Read the string describing the reason for a connection failure,
  // and throw an exception.
  //

  void readConnFailedReason() throws Exception {
    int reasonLen = is.readInt();
    byte[] reason = new byte[reasonLen];
    readFully(reason);
    String reasonString = new String(reason);
    Log.v(TAG, reasonString);
    throw new Exception(reasonString);
  }

  void prepareDH() throws Exception
  {
    long gen = is.readLong();
    long mod = is.readLong();
    dh_resp = is.readLong();
    
    dh = new DH(gen,mod);
    long pub = dh.createInterKey();
    
    os.write(DH.longToBytes(pub));
  }
  
  void authenticateDH(String us, String pw) throws Exception
  {
    long key = dh.createEncryptionKey(dh_resp);
    
    DesCipher des = new DesCipher(DH.longToBytes(key));
    
    byte user[] = new byte[256];
    byte passwd[] = new byte[64];
    int i;
    System.arraycopy(us.getBytes(),0,user,0,us.length());
    if(us.length() < 256)
    {
      for(i=us.length(); i<256; i++)
      {
        user[i]=0;
      }
    }
    System.arraycopy(pw.getBytes(),0,passwd,0,pw.length());
    if(pw.length() < 64)
    {
      for(i=pw.length(); i<64; i++)
      {
        passwd[i]=0;
      }
    }
    
    des.encryptText(user,user,DH.longToBytes(key));
    des.encryptText(passwd,passwd,DH.longToBytes(key));
    
    os.write(user);
    os.write(passwd);
    
    readSecurityResult("VNC authentication");
  }
  //
  // Initialize capability lists (TightVNC protocol extensions).
  //

  void initCapabilities() {
    tunnelCaps    = new CapsContainer();
    authCaps      = new CapsContainer();
    serverMsgCaps = new CapsContainer();
    clientMsgCaps = new CapsContainer();
    encodingCaps  = new CapsContainer();

    // Supported authentication methods
    authCaps.add(AuthNone, StandardVendor, SigAuthNone,
         "No authentication");
    authCaps.add(AuthVNC, StandardVendor, SigAuthVNC,
         "Standard VNC password authentication");

    // Supported encoding types
    encodingCaps.add(EncodingCopyRect, StandardVendor,
             SigEncodingCopyRect, "Standard CopyRect encoding");
    encodingCaps.add(EncodingRRE, StandardVendor,
             SigEncodingRRE, "Standard RRE encoding");
    encodingCaps.add(EncodingCoRRE, StandardVendor,
             SigEncodingCoRRE, "Standard CoRRE encoding");
    encodingCaps.add(EncodingHextile, StandardVendor,
             SigEncodingHextile, "Standard Hextile encoding");
    encodingCaps.add(EncodingZRLE, StandardVendor,
             SigEncodingZRLE, "Standard ZRLE encoding");
    encodingCaps.add(EncodingZlib, TridiaVncVendor,
             SigEncodingZlib, "Zlib encoding");
    encodingCaps.add(EncodingTight, TightVncVendor,
             SigEncodingTight, "Tight encoding");

    // Supported pseudo-encoding types
    encodingCaps.add(EncodingCompressLevel0, TightVncVendor,
             SigEncodingCompressLevel0, "Compression level");
    encodingCaps.add(EncodingQualityLevel0, TightVncVendor,
             SigEncodingQualityLevel0, "JPEG quality level");
    encodingCaps.add(EncodingXCursor, TightVncVendor,
             SigEncodingXCursor, "X-style cursor shape update");
    encodingCaps.add(EncodingRichCursor, TightVncVendor,
             SigEncodingRichCursor, "Rich-color cursor shape update");
    encodingCaps.add(EncodingPointerPos, TightVncVendor,
             SigEncodingPointerPos, "Pointer position update");
    encodingCaps.add(EncodingLastRect, TightVncVendor,
             SigEncodingLastRect, "LastRect protocol extension");
    encodingCaps.add(EncodingNewFBSize, TightVncVendor,
             SigEncodingNewFBSize, "Framebuffer size change");
  }

  // Perform TLS handshake (AnonTLS)
  void performTLSHandshake () throws Exception {
    android.util.Log.i(TAG, "Performing TLS handshake.");

    // Try using SSL
       SSLSocketToMe ssl;
       try {
           ssl = new SSLSocketToMe(host, port);
        sock = ssl.connectSock(sock);
        sock.setTcpNoDelay(true);
        is = new DataInputStream(new BufferedInputStream(sock.getInputStream(), 8192));
        os = sock.getOutputStream();
       } catch (Exception e) {
           e.printStackTrace();
           throw new IOException(e.getMessage());
       }
  }
  
  //
  // Setup tunneling (TightVNC protocol extensions)
  //

  void setupTunneling() throws IOException {
    int nTunnelTypes = is.readInt();
    if (nTunnelTypes != 0) {
      readCapabilityList(tunnelCaps, nTunnelTypes);

      // We don't support tunneling yet.
      writeInt(NoTunneling);
    }
  }

  //
  // Negotiate authentication scheme (TightVNC protocol extensions)
  //

  int negotiateAuthenticationTight() throws Exception {
    int nAuthTypes = is.readInt();
    if (nAuthTypes == 0)
      return AuthNone;

    readCapabilityList(authCaps, nAuthTypes);
    for (int i = 0; i < authCaps.numEnabled(); i++) {
      int authType = authCaps.getByOrder(i);
      if (authType == AuthNone || authType == AuthVNC) {
    writeInt(authType);
    return authType;
      }
    }
    throw new Exception("No suitable authentication scheme found");
  }

  //
  // Read a capability list (TightVNC protocol extensions)
  //

  void readCapabilityList(CapsContainer caps, int count) throws IOException {
    int code;
    byte[] vendor = new byte[4];
    byte[] name = new byte[8];
    for (int i = 0; i < count; i++) {
      code = is.readInt();
      readFully(vendor);
      readFully(name);
      caps.enable(new CapabilityInfo(code, vendor, name));
    }
  }

  //
  // Write a 32-bit integer into the output stream.
  //

  byte[] writeIntBuffer = new byte[4];
  void writeInt(int value) throws IOException {
    writeIntBuffer[0] = (byte) ((value >> 24) & 0xff);
    writeIntBuffer[1] = (byte) ((value >> 16) & 0xff);
    writeIntBuffer[2] = (byte) ((value >> 8) & 0xff);
    writeIntBuffer[3] = (byte) (value & 0xff);
    os.write(writeIntBuffer);
  }

  //
  // Write the client initialisation message
  //

  void writeClientInit() throws IOException {
    /*- if (viewer.options.shareDesktop) {
      os.write(1);
    } else {
      os.write(0);
    }
    viewer.options.disableShareDesktop();
    */
      os.write(shareDesktop);
  }


  //
  // Read the server initialisation message
  //

  String desktopName;
  int framebufferWidth, framebufferHeight;
  int bitsPerPixel, depth;
  boolean bigEndian, trueColour;
  int redMax, greenMax, blueMax, redShift, greenShift, blueShift;

  void readServerInit() throws IOException {
    framebufferWidth = is.readUnsignedShort();
    framebufferHeight = is.readUnsignedShort();
    bitsPerPixel = is.readUnsignedByte();
    depth = is.readUnsignedByte();
    bigEndian = (is.readUnsignedByte() != 0);
    trueColour = (is.readUnsignedByte() != 0);
    redMax = is.readUnsignedShort();
    greenMax = is.readUnsignedShort();
    blueMax = is.readUnsignedShort();
    redShift = is.readUnsignedByte();
    greenShift = is.readUnsignedByte();
    blueShift = is.readUnsignedByte();
    byte[] pad = new byte[3];
    readFully(pad);
    int nameLength = is.readInt();
    byte[] name = new byte[nameLength];
    readFully(name);
    desktopName = new String(name);

    // Read interaction capabilities (TightVNC protocol extensions)
    if (protocolTightVNC) {
      int nServerMessageTypes = is.readUnsignedShort();
      int nClientMessageTypes = is.readUnsignedShort();
      int nEncodingTypes = is.readUnsignedShort();
      is.readUnsignedShort();
      readCapabilityList(serverMsgCaps, nServerMessageTypes);
      readCapabilityList(clientMsgCaps, nClientMessageTypes);
      readCapabilityList(encodingCaps, nEncodingTypes);
    }

    inNormalProtocol = true;
  }


  //
  // Create session file and write initial protocol messages into it.
  //
  /*-
  void startSession(String fname) throws IOException {
    rec = new SessionRecorder(fname);
    rec.writeHeader();
    rec.write(versionMsg_3_3.getBytes());
    rec.writeIntBE(SecTypeNone);
    rec.writeShortBE(framebufferWidth);
    rec.writeShortBE(framebufferHeight);
    byte[] fbsServerInitMsg =    {
      32, 24, 0, 1, 0,
      (byte)0xFF, 0, (byte)0xFF, 0, (byte)0xFF,
      16, 8, 0, 0, 0, 0
    };
    rec.write(fbsServerInitMsg);
    rec.writeIntBE(desktopName.length());
    rec.write(desktopName.getBytes());
    numUpdatesInSession = 0;

    // FIXME: If there were e.g. ZRLE updates only, that should not
    //        affect recording of Zlib and Tight updates. So, actually
    //        we should maintain separate flags for Zlib, ZRLE and
    //        Tight, instead of one ``wereZlibUpdates'' variable.
    //
    if (wereZlibUpdates)
      recordFromBeginning = false;

    zlibWarningShown = false;
    tightWarningShown = false;
  }

  //
  // Close session file.
  //

  void closeSession() throws IOException {
    if (rec != null) {
      rec.close();
      rec = null;
    }
  }
  */

  //
  // Set new framebuffer size
  //

  void setFramebufferSize(int width, int height) {
    framebufferWidth = width;
    framebufferHeight = height;
  }


  //
  // Read the server message type
  //

  int readServerMessageType() throws IOException {
    return is.readUnsignedByte();

    // If the session is being recorded:
    /*-
    if (rec != null) {
      if (msgType == Bell) {    // Save Bell messages in session files.
    rec.writeByte(msgType);
    if (numUpdatesInSession > 0)
      rec.flush();
      }
    }
    */
  }


  //
  // Read a FramebufferUpdate message
  //

  int updateNRects;

  void readFramebufferUpdate() throws IOException {
    is.readByte();
    updateNRects = is.readUnsignedShort();

    // If the session is being recorded:
    /*-
    if (rec != null) {
      rec.writeByte(FramebufferUpdate);
      rec.writeByte(0);
      rec.writeShortBE(updateNRects);
    }
    
    
    numUpdatesInSession++;*/
  }

  // Read a FramebufferUpdate rectangle header

  int updateRectX, updateRectY, updateRectW, updateRectH, updateRectEncoding;

  void readFramebufferUpdateRectHdr() throws Exception {
    updateRectX = is.readUnsignedShort();
    updateRectY = is.readUnsignedShort();
    updateRectW = is.readUnsignedShort();
    updateRectH = is.readUnsignedShort();
    updateRectEncoding = is.readInt();

    /*
    if (updateRectEncoding == EncodingZlib ||
        updateRectEncoding == EncodingZRLE ||
        updateRectEncoding == EncodingTight)
        wereZlibUpdates = true;

    // If the session is being recorded:
    if (rec != null) {
      if (numUpdatesInSession > 1)
    rec.flush();        // Flush the output on each rectangle.
      rec.writeShortBE(updateRectX);
      rec.writeShortBE(updateRectY);
      rec.writeShortBE(updateRectW);
      rec.writeShortBE(updateRectH);
      if (updateRectEncoding == EncodingZlib && !recordFromBeginning) {
    // Here we cannot write Zlib-encoded rectangles because the
    // decoder won't be able to reproduce zlib stream state.
    if (!zlibWarningShown) {
      System.out.println("Warning: Raw encoding will be used " +
                 "instead of Zlib in recorded session.");
      zlibWarningShown = true;
    }
    rec.writeIntBE(EncodingRaw);
      } else {
    rec.writeIntBE(updateRectEncoding);
    if (updateRectEncoding == EncodingTight && !recordFromBeginning &&
        !tightWarningShown) {
      System.out.println("Warning: Re-compressing Tight-encoded " +
                 "updates for session recording.");
      tightWarningShown = true;
    }
      }
    }
    */

    /*
    if (updateRectEncoding != RfbProto.EncodingPointerPos &&
        ( updateRectEncoding < 0 || updateRectEncoding > MaxNormalEncoding ))
        return;

    if (updateRectX + updateRectW > framebufferWidth ||
        updateRectY + updateRectH > framebufferHeight) {
        throw new Exception("Framebuffer update rectangle too large: " +
                            updateRectW + "x" + updateRectH + " at (" +
                            updateRectX + "," + updateRectY + ")");
    }
    */
  }

  // Read CopyRect source X and Y.

  int copyRectSrcX, copyRectSrcY;

  void readCopyRect() throws IOException {
    copyRectSrcX = is.readUnsignedShort();
    copyRectSrcY = is.readUnsignedShort();

    // If the session is being recorded:
    /*-
    if (rec != null) {
      rec.writeShortBE(copyRectSrcX);
      rec.writeShortBE(copyRectSrcY);
    }
    */
  }


  //
  // Read a ServerCutText message
  //

  String readServerCutText() throws IOException {
    byte[] pad = new byte[3];
    readFully(pad);
    int len = is.readInt();
    byte[] text = new byte[len];
    readFully(text);
    return new String(text);
  }


  //
  // Read an integer in compact representation (1..3 bytes).
  // Such format is used as a part of the Tight encoding.
  // Also, this method records data if session recording is active and
  // the viewer's recordFromBeginning variable is set to true.
  //

  int readCompactLen() throws IOException {
    int[] portion = new int[3];
    portion[0] = is.readUnsignedByte();
    //int byteCount = 1;
    int len = portion[0] & 0x7F;
    if ((portion[0] & 0x80) != 0) {
      portion[1] = is.readUnsignedByte();
      //byteCount++;
      len |= (portion[1] & 0x7F) << 7;
      if ((portion[1] & 0x80) != 0) {
    portion[2] = is.readUnsignedByte();
    //byteCount++;
    len |= (portion[2] & 0xFF) << 14;
      }
    }
    /*-
    if (rec != null && recordFromBeginning)
      for (int i = 0; i < byteCount; i++)
    rec.writeByte(portion[i]);
    */
    return len;
  }


  //
  // Write a FramebufferUpdateRequest message
  //

  byte[] framebufferUpdateRequest = new byte[10];
  public synchronized void writeFramebufferUpdateRequest(int x, int y, int w, int h,
                     boolean incremental) {
    framebufferUpdateRequest[0] = (byte) FramebufferUpdateRequest;
    framebufferUpdateRequest[1] = (byte) (incremental ? 1 : 0);
    framebufferUpdateRequest[2] = (byte) ((x >> 8) & 0xff);
    framebufferUpdateRequest[3] = (byte) (x & 0xff);
    framebufferUpdateRequest[4] = (byte) ((y >> 8) & 0xff);
    framebufferUpdateRequest[5] = (byte) (y & 0xff);
    framebufferUpdateRequest[6] = (byte) ((w >> 8) & 0xff);
    framebufferUpdateRequest[7] = (byte) (w & 0xff);
    framebufferUpdateRequest[8] = (byte) ((h >> 8) & 0xff);
    framebufferUpdateRequest[9] = (byte) (h & 0xff);

    try {
        os.write(framebufferUpdateRequest);
    } catch (IOException e) {
        Log.e(TAG, "Could not write framebuffer update request.");
        e.printStackTrace();
    }
  }


  //
  // Write a SetPixelFormat message
  //

  public synchronized void writeSetPixelFormat(int bitsPerPixel, int depth, boolean bigEndian,
               boolean trueColour, int redMax, int greenMax, int blueMax,
               int redShift, int greenShift, int blueShift, boolean fGreyScale) // sf@2005)
  {
    byte[] b = new byte[20];

    b[0]  = (byte) SetPixelFormat;
    b[4]  = (byte) bitsPerPixel;
    b[5]  = (byte) depth;
    b[6]  = (byte) (bigEndian ? 1 : 0);
    b[7]  = (byte) (trueColour ? 1 : 0);
    b[8]  = (byte) ((redMax >> 8) & 0xff);
    b[9]  = (byte) (redMax & 0xff);
    b[10] = (byte) ((greenMax >> 8) & 0xff);
    b[11] = (byte) (greenMax & 0xff);
    b[12] = (byte) ((blueMax >> 8) & 0xff);
    b[13] = (byte) (blueMax & 0xff);
    b[14] = (byte) redShift;
    b[15] = (byte) greenShift;
    b[16] = (byte) blueShift;
    b[17] = (byte) (fGreyScale ? 1 : 0); // sf@2005

    try {
        os.write(b);
    } catch (IOException e) {
        Log.e(TAG, "Could not write setPixelFormat message to VNC server.");
        e.printStackTrace();
    }
  }


  //
  // Write a FixColourMapEntries message.  The values in the red, green and
  // blue arrays are from 0 to 65535.
  //

  synchronized void writeFixColourMapEntries(int firstColour, int nColours,
                int[] red, int[] green, int[] blue)
       throws IOException
  {
    byte[] b = new byte[6 + nColours * 6];

    b[0] = (byte) FixColourMapEntries;
    b[2] = (byte) ((firstColour >> 8) & 0xff);
    b[3] = (byte) (firstColour & 0xff);
    b[4] = (byte) ((nColours >> 8) & 0xff);
    b[5] = (byte) (nColours & 0xff);

    for (int i = 0; i < nColours; i++) {
      b[6 + i * 6]     = (byte) ((red[i] >> 8) & 0xff);
      b[6 + i * 6 + 1] = (byte) (red[i] & 0xff);
      b[6 + i * 6 + 2] = (byte) ((green[i] >> 8) & 0xff);
      b[6 + i * 6 + 3] = (byte) (green[i] & 0xff);
      b[6 + i * 6 + 4] = (byte) ((blue[i] >> 8) & 0xff);
      b[6 + i * 6 + 5] = (byte) (blue[i] & 0xff);
    }
 
    os.write(b);
  }


  //
  // Write a SetEncodings message
  //

  synchronized void writeSetEncodings(int[] encs, int len) throws IOException {
    byte[] b = new byte[4 + 4 * len];

    b[0] = (byte) SetEncodings;
    b[2] = (byte) ((len >> 8) & 0xff);
    b[3] = (byte) (len & 0xff);

    for (int i = 0; i < len; i++) {
      b[4 + 4 * i] = (byte) ((encs[i] >> 24) & 0xff);
      b[5 + 4 * i] = (byte) ((encs[i] >> 16) & 0xff);
      b[6 + 4 * i] = (byte) ((encs[i] >> 8) & 0xff);
      b[7 + 4 * i] = (byte) (encs[i] & 0xff);
    }

    os.write(b);
  }


  //
  // Write a ClientCutText message
  //

  synchronized void writeClientCutText(String text, int length) throws IOException {
    if (viewOnly)
        return;
    
    byte[] b = new byte[8 + length];

    b[0] = (byte) ClientCutText;
    b[4] = (byte) ((text.length() >> 24) & 0xff);
    b[5] = (byte) ((text.length() >> 16) & 0xff);
    b[6] = (byte) ((text.length() >> 8) & 0xff);
    b[7] = (byte) (text.length() & 0xff);

    System.arraycopy(text.getBytes(), 0, b, 8, length);

    os.write(b);
  }


  //
  // A buffer for putting pointer and keyboard events before being sent.  This
  // is to ensure that multiple RFB events generated from a single Java Event 
  // will all be sent in a single network packet.  The maximum possible
  // length is 4 modifier down events, a single key event followed by 4
  // modifier up events i.e. 9 key events or 72 bytes.
  //

  byte[] eventBuf = new byte[72];
  int eventBufLen;


  /**
   * Write a pointer event message.  We may need to send modifier key events
   * around it to set the correct modifier state.
   * @param x
   * @param y
   * @param modifiers
   * @param pointerMask
   * @throws IOException
   */
  public synchronized void writePointerEvent( int x, int y, int modifiers, int pointerMask) {
        if (viewOnly)
            return;
        
        eventBufLen = 0;
        writeModifierKeyEvents(modifiers);

        eventBuf[eventBufLen++] = (byte) PointerEvent;
        eventBuf[eventBufLen++] = (byte) pointerMask;
        eventBuf[eventBufLen++] = (byte) ((x >> 8) & 0xff);
        eventBuf[eventBufLen++] = (byte) (x & 0xff);
        eventBuf[eventBufLen++] = (byte) ((y >> 8) & 0xff);
        eventBuf[eventBufLen++] = (byte) (y & 0xff);

        //
        // Always release all modifiers after an "up" event
        //

        if (pointerMask == 0) {
          writeModifierKeyEvents(0);
        }

        try {
            os.write(eventBuf, 0, eventBufLen);
        } catch (IOException e) {
            Log.e (TAG, "Failed to write pointer event to VNC server.");
            e.printStackTrace();
        }      
  }

  void writeCtrlAltDel() throws IOException {
      final int DELETE = 0xffff;
      final int CTRLALT = RemoteKeyboard.CTRL_MASK | RemoteKeyboard.ALT_MASK;
      try {
          // Press
          eventBufLen = 0;
          writeModifierKeyEvents(CTRLALT);
          writeKeyEvent(DELETE, true);
          os.write(eventBuf, 0, eventBufLen);
          
          // Release
          eventBufLen = 0;
          writeModifierKeyEvents(CTRLALT);
          writeKeyEvent(DELETE, false);
          
          // Reset VNC server modifiers state
          writeModifierKeyEvents(0);
          os.write(eventBuf, 0, eventBufLen);
      } catch (IOException e) {
          e.printStackTrace();
      }
  }
    
  //
  // Write a key event message.  We may need to send modifier key events
  // around it to set the correct modifier state.  Also we need to translate
  // from the Java key values to the X keysym values used by the RFB protocol.
  //
  public synchronized void writeKeyEvent(int keySym, int metaState, boolean down) {
    if (viewOnly)
        return;
    
    eventBufLen = 0;
    if (down)
        writeModifierKeyEvents(metaState);
    if (keySym > 0)
        writeKeyEvent(keySym, down);

    // Always release all modifiers after an "up" event
    if (!down) {
        writeModifierKeyEvents(0);
    }

    try {
        os.write(eventBuf, 0, eventBufLen);
    } catch (IOException e) {
        Log.e(TAG, "Failed to write key event to VNC server.");
        e.printStackTrace();
    }
  }
  
  


  //
  // Add a raw key event with the given X keysym to eventBuf.
  //

  private void writeKeyEvent(int keysym, boolean down) {
    if (viewOnly)
        return;
    
    eventBuf[eventBufLen++] = (byte) KeyboardEvent;
    eventBuf[eventBufLen++] = (byte) (down ? 1 : 0);
    eventBuf[eventBufLen++] = (byte) 0;
    eventBuf[eventBufLen++] = (byte) 0;
    eventBuf[eventBufLen++] = (byte) ((keysym >> 24) & 0xff);
    eventBuf[eventBufLen++] = (byte) ((keysym >> 16) & 0xff);
    eventBuf[eventBufLen++] = (byte) ((keysym >> 8) & 0xff);
    eventBuf[eventBufLen++] = (byte) (keysym & 0xff);
  }
  

  //
  // Write key events to set the correct modifier state.
  //

  int oldModifiers = 0;

  void writeModifierKeyEvents(int newModifiers) {
    if ((newModifiers & RemoteKeyboard.CTRL_MASK) != (oldModifiers & RemoteKeyboard.CTRL_MASK))
      writeKeyEvent(0xffe3, (newModifiers & RemoteKeyboard.CTRL_MASK) != 0);

    if ((newModifiers & RemoteKeyboard.SHIFT_MASK) != (oldModifiers & RemoteKeyboard.SHIFT_MASK))
      writeKeyEvent(0xffe1, (newModifiers & RemoteKeyboard.SHIFT_MASK) != 0);

    if ((newModifiers & RemoteKeyboard.META_MASK) != (oldModifiers & RemoteKeyboard.META_MASK))
      writeKeyEvent(0xffe7, (newModifiers & RemoteKeyboard.META_MASK) != 0);

    if ((newModifiers & RemoteKeyboard.ALT_MASK) != (oldModifiers & RemoteKeyboard.ALT_MASK))
      writeKeyEvent(0xffe9, (newModifiers & RemoteKeyboard.ALT_MASK) != 0);
    
    if ((newModifiers & RemoteKeyboard.SUPER_MASK) != (oldModifiers & RemoteKeyboard.SUPER_MASK))
        writeKeyEvent(0xffeb, (newModifiers & RemoteKeyboard.SUPER_MASK) != 0);

    oldModifiers = newModifiers;
  }
  //
  // Compress and write the data into the recorded session file. This
  // method assumes the recording is on (rec != null).
  //


  public void startTiming() {
    timing = true;

    // Carry over up to 1s worth of previous rate for smoothing.

    if (timeWaitedIn100us > 10000) {
      timedKbits = timedKbits * 10000 / timeWaitedIn100us;
      timeWaitedIn100us = 10000;
    }
  }

  public void stopTiming() {
    timing = false; 
    if (timeWaitedIn100us < timedKbits/2)
      timeWaitedIn100us = timedKbits/2; // upper limit 20Mbit/s
  }

  public long kbitsPerSecond() {
    return timedKbits * 10000 / timeWaitedIn100us;
  }

  public long timeWaited() {
    return timeWaitedIn100us;
  }

  public void readFully(byte b[]) throws IOException {
    readFully(b, 0, b.length);
  }

  public void readFully(byte b[], int off, int len) throws IOException {
      // TODO: Try reenabling timing and set color according to bandwidth
    /*
    long before = 0;
    timing = false; // for test
    
    if (timing)
      before = System.currentTimeMillis();
     */

    is.readFully(b, off, len);

    /*
    if (timing) {
      long after = System.currentTimeMillis();
      long newTimeWaited = (after - before) * 10;
      int newKbits = len * 8 / 1000;

      // limit rate to between 10kbit/s and 40Mbit/s

      if (newTimeWaited > newKbits*1000) newTimeWaited = newKbits*1000;
      if (newTimeWaited < newKbits/4)    newTimeWaited = newKbits/4;

      timeWaitedIn100us += newTimeWaited;
      timedKbits += newKbits;
    }
    */
  }

    synchronized void writeOpenChat() throws Exception {
        os.write(TextChat); // byte type
        os.write(0); // byte pad 1
        os.write(0); // byte pad 2
        os.write(0); // byte pad 2
        writeInt(CHAT_OPEN); // int message length
    }

    synchronized void writeCloseChat() throws Exception {
        os.write(TextChat); // byte type
        os.write(0); // byte pad 1
        os.write(0); // byte pad 2
        os.write(0); // byte pad 2
        writeInt(CHAT_CLOSE); // int message length
    }

    synchronized void writeFinishedChat() throws Exception {
        os.write(TextChat); // byte type
        os.write(0); // byte pad 1
        os.write(0); // byte pad 2
        os.write(0); // byte pad 2
        writeInt(CHAT_FINISHED); // int message length
    }

    String readTextChatMsg() throws Exception {
        byte[] pad = new byte[3];
        readFully(pad);
        int len = is.readInt();
        if (len == CHAT_OPEN) {
            // Remote user requests chat
            ///viewer.openChat();
            // Respond to chat request
            writeOpenChat();
            return null;
        } else if (len == CHAT_CLOSE) {
            // Remote user ends chat
            ///viewer.closeChat();
            return null;
        } else if (len == CHAT_FINISHED) {
            // Remote user says chat finished.
            // Not sure why I should care about this state.
            return null;
        } else {
            // Remote user sends message!!
            if (len > 0) {
                byte[] msg = new byte[len];
                readFully(msg);
                return new String(msg);
            }
        }
        return null;
    }

    public synchronized void writeChatMessage(String msg) throws Exception {
        os.write(TextChat); // byte type
        os.write(0); // byte pad 1
        os.write(0); // byte pad 2
        os.write(0); // byte pad 2
        byte [] bytes = msg.getBytes("8859_1");
        byte [] outgoing = bytes;
        if (bytes.length > 4096) {
            outgoing = new byte[4096];
            System.arraycopy(bytes, 0, outgoing, 0, 4096);
        }
        writeInt(outgoing.length); // int message length
        os.write(outgoing); // message
    }
    
    // The following methods are implementations of the RfbConnectable interface
    @Override
    public int framebufferWidth () {
        return framebufferWidth;
    }

    @Override
    public int framebufferHeight () {
        return framebufferHeight;
    }

    @Override
    public String desktopName () {
        return desktopName;
    }

    @Override
    public void requestUpdate (boolean incremental) {
        writeFramebufferUpdateRequest(0, 0, framebufferWidth, framebufferHeight, incremental);
    }

    @Override
    public void writeClientCutText(String text) {
        try {
            writeClientCutText(text, text.length());
        } catch (IOException e) {
            Log.e(TAG, "Could not write text to VNC server clipboard.");
            e.printStackTrace();
        }
    }

    @Override
    public void setIsInNormalProtocol(boolean state) {
          this.inNormalProtocol = state;
    }
    
    @Override
    public boolean isInNormalProtocol() {
        return this.inNormalProtocol;
    }

    public String getEncoding() {
        switch (preferredEncoding) {
        case RfbProto.EncodingRaw:
            return "RAW";
        case RfbProto.EncodingTight:
            return "TIGHT";
        case RfbProto.EncodingCoRRE:
            return "CoRRE";
        case RfbProto.EncodingHextile:
            return "HEXTILE";
        case RfbProto.EncodingRRE:
            return "RRE";
        case RfbProto.EncodingZlib:
            return "ZLIB";
        case RfbProto.EncodingZRLE:
            return "ZRLE";
        }
        return "";
    }

    private void setEncodings(boolean useLocalCursor) {
        if (!inNormalProtocol)
            return;

        int[] encodings = new int[20];
        int nEncodings = 0;

        encodings[nEncodings++] = preferredEncoding;
        encodings[nEncodings++] = RfbProto.EncodingTight;
        encodings[nEncodings++] = RfbProto.EncodingZRLE;
        encodings[nEncodings++] = RfbProto.EncodingHextile;
        encodings[nEncodings++] = RfbProto.EncodingZlib;
        encodings[nEncodings++] = RfbProto.EncodingCoRRE;
        encodings[nEncodings++] = RfbProto.EncodingRRE;

        encodings[nEncodings++] = RfbProto.EncodingCopyRect;

        encodings[nEncodings++] = RfbProto.EncodingCompressLevel0 + compressLevel;
        encodings[nEncodings++] = RfbProto.EncodingQualityLevel0 + jpegQuality;

        if (!useLocalCursor) {
            encodings[nEncodings++] = RfbProto.EncodingXCursor;
            encodings[nEncodings++] = RfbProto.EncodingRichCursor;
        }

        encodings[nEncodings++] = RfbProto.EncodingPointerPos;
        encodings[nEncodings++] = RfbProto.EncodingLastRect;
        encodings[nEncodings++] = RfbProto.EncodingNewFBSize;

        boolean encodingsWereChanged = false;
        if (nEncodings != nEncodingsSaved) {
            encodingsWereChanged = true;
        } else {
            for (int i = 0; i < nEncodings; i++) {
                if (encodings[i] != encodingsSaved[i]) {
                    encodingsWereChanged = true;
                    break;
                }
            }
        }

        if (encodingsWereChanged) {
            try {
                writeSetEncodings(encodings, nEncodings);
            } catch (Exception e) {
                e.printStackTrace();
            }
            encodingsSaved = encodings;
            nEncodingsSaved = nEncodings;
        }
    }
    

    public void processProtocol (RemoteCanvas vncCanvas, boolean useLocalCursor) throws Exception {
        boolean exitforloop = false;
        int msgType = 0;

        try {
            setEncodings(useLocalCursor);
            vncCanvas.writeFullUpdateRequest(false);

            //
            // main dispatch loop
            //
            while (maintainConnection) {
                exitforloop = false;
                if (!vncCanvas.useFull) {
                    vncCanvas.syncScroll();
                    // Read message type from the server.
                    msgType = readServerMessageType();
                    vncCanvas.doneWaiting();
                } else
                    msgType = readServerMessageType();

                // Process the message depending on its type.
                switch (msgType) {
                case RfbProto.FramebufferUpdate:
                    readFramebufferUpdate();

                    for (int i = 0; i < updateNRects; i++) {
                        readFramebufferUpdateRectHdr();

                        switch (updateRectEncoding) {
                        case RfbProto.EncodingTight:
                            decoder.handleTightRect(this, updateRectX, updateRectY, updateRectW, updateRectH);
                            break;
                        case RfbProto.EncodingPointerPos:
                            vncCanvas.softCursorMove(updateRectX, updateRectY);
                            break;
                        case RfbProto.EncodingXCursor:
                        case RfbProto.EncodingRichCursor:
                            decoder.handleCursorShapeUpdate(this, updateRectEncoding, updateRectX, updateRectY,
                                                              updateRectW, updateRectH);
                            break;
                        case RfbProto.EncodingLastRect:
                            exitforloop = true;
                            break;
                        case RfbProto.EncodingCopyRect:
                            decoder.handleCopyRect(this, updateRectX, updateRectY, updateRectW, updateRectH);
                            break;
                        case RfbProto.EncodingNewFBSize:
                            setFramebufferSize(updateRectW, updateRectH);
                            vncCanvas.updateFBSize();
                            exitforloop = true;
                            break;
                        case RfbProto.EncodingRaw:
                            decoder.handleRawRect(this, updateRectX, updateRectY, updateRectW, updateRectH);
                            break;
                        case RfbProto.EncodingRRE:
                            decoder.handleRRERect(this, updateRectX, updateRectY, updateRectW, updateRectH);
                            break;
                        case RfbProto.EncodingCoRRE:
                            decoder.handleCoRRERect(this, updateRectX, updateRectY, updateRectW, updateRectH);
                            break;
                        case RfbProto.EncodingHextile:
                            decoder.handleHextileRect(this, updateRectX, updateRectY, updateRectW, updateRectH);
                            break;
                        case RfbProto.EncodingZRLE:
                            decoder.handleZRLERect(this, updateRectX, updateRectY, updateRectW, updateRectH);
                            break;
                        case RfbProto.EncodingZlib:
                            decoder.handleZlibRect(this, updateRectX, updateRectY, updateRectW, updateRectH);
                            break;
                        default:
                            Log.e(TAG, "Unknown RFB rectangle encoding " + updateRectEncoding +
                                        " (0x" + Integer.toHexString(updateRectEncoding) + ")");
                        }
                        
                        if (exitforloop) {
                            exitforloop = false;
                            break;
                        }
                    }

                    if (decoder.isChangedColorModel()) {
                        decoder.setPixelFormat(this);
                        //setEncodings(useLocalCursor);
                        vncCanvas.writeFullUpdateRequest(false);
                    } else {
                        //setEncodings(useLocalCursor);
                        vncCanvas.writeFullUpdateRequest(true);
                    }
                    break;

                case RfbProto.SetColourMapEntries:
                    throw new Exception("Can't handle SetColourMapEntries message");

                case RfbProto.Bell:
                    vncCanvas.displayShortToastMessage("VNC Beep");
                    break;

                case RfbProto.ServerCutText:
                    vncCanvas.serverJustCutText = true;
                    vncCanvas.setClipboardText(readServerCutText());
                    break;

                case RfbProto.TextChat:
                    // UltraVNC extension
                    String msg = readTextChatMsg();
                    if (msg != null && msg.length() > 0) {
                        // TODO implement chat interface
                    }
                    break;

                default:
                    throw new Exception("Unknown RFB message type " + msgType);
                }
            }
        } catch (Exception e) {
            closeSocket();
            throw e;
        } finally {
            closeSocket();
            Log.v(TAG, "Closing VNC Connection");
        }
        closeSocket();
    }


    @Override
    public void requestResolution(int x, int y) {
        // TODO Auto-generated method stub
        
    }

}
