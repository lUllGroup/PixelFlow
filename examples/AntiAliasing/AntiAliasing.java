/**
 * 
 * PixelFlow | Copyright (C) 2017 Thomas Diewald (www.thomasdiewald.com)
 * 
 * src  - www.github.com/diwi/PixelFlow
 * 
 * A Processing/Java library for high performance GPU-Computing.
 * MIT License: https://opensource.org/licenses/MIT
 * 
 */


package AntiAliasing;

import java.util.Locale;
import java.util.Random;

import com.thomasdiewald.pixelflow.java.DwPixelFlow;
import com.thomasdiewald.pixelflow.java.antialiasing.FXAA.FXAA;
import com.thomasdiewald.pixelflow.java.antialiasing.GBAA.GBAA;
import com.thomasdiewald.pixelflow.java.antialiasing.SMAA.SMAA;
import com.thomasdiewald.pixelflow.java.dwgl.DwGLTextureUtils;
import com.thomasdiewald.pixelflow.java.geometry.DwCube;
import com.thomasdiewald.pixelflow.java.geometry.DwMeshUtils;
import com.thomasdiewald.pixelflow.java.imageprocessing.filter.DwFilter;
import com.thomasdiewald.pixelflow.java.render.skylight.DwSceneDisplay;

import peasy.*;
import processing.core.PApplet;
import processing.core.PConstants;
import processing.core.PFont;
import processing.core.PImage;
import processing.core.PShape;
import processing.opengl.PGraphics3D;
import processing.opengl.PGraphicsOpenGL;


public class AntiAliasing extends PApplet {
  
  //
  // This example compares several Anti-Aliasing Algorithms.
  // 
  // 1) NOAA
  //    no AntiAliasing, default rendering mode
  //
  //
  // 2) MSAA - MultiSample AntiAliasing (8x)
  //    build in OpenGL AA, best quality, performance intensive
  //    https://www.khronos.org/opengl/wiki/Multisampling
  //
  //
  // 3) SMAA - Enhances SubPixel Morphological AntiAliasing
  //    PostProcessing, 3 passes, nice quality
  //    SMAA.h, Version 2.8: 
  //    http://www.iryoku.com/smaa/
  //    
  //
  // 4) FXAA - Fast Approximate AntiAliasing
  //    PostProcessing, 1 pass, good quality - a bit blurry sometimes
  //    created "by Timothy Lottes under NVIDIA", FXAA_WhitePaper.pdf
  //    FXAA3_11.h, Version 3.11: 
  //    https://docs.nvidia.com/gameworks/content/gameworkslibrary/graphicssamples/opengl_samples/fxaa.htm
  //
  // 5) GBAA - GeometryBuffer AntiAliasing
  //    Shader Pipeline - Distance-to-Edge (GeometryShader) used for blending
  //    created by Emil Persson, 2011, http://www.humus.name
  //    article: http://www.humus.name/index.php?page=3D&ID=87
  //
  //
  //
  // controls:
  //
  // key '1': NOAA
  // key '2': MSAA(8)
  // key '3': SMAA
  // key '4': FXAA
  // key '5': GBAA
  //
  // ALT + LMB: Camera ROTATE
  // ALT + MMB: Camera PAN
  // ALT + RMB: Camera ZOOM
  //


  

  boolean START_FULLSCREEN = !true;

  int viewport_w = 1280;
  int viewport_h = 720;
  int viewport_x = 230;
  int viewport_y = 0;

  // camera control
  PeasyCam peasycam;
  
  // library context
  DwPixelFlow context;
  
  // AntiAliasing
  FXAA fxaa;
  SMAA smaa;
  GBAA gbaa;
  
  // AntiAliasing render targets
  PGraphics3D pg_render_noaa;
  PGraphics3D pg_render_smaa;
  PGraphics3D pg_render_fxaa;
  PGraphics3D pg_render_msaa;
  PGraphics3D pg_render_gbaa;
  
  // toggling active AntiAliasing-Mode
  AA_MODE aa_mode = AA_MODE.NoAA;
  SMAA_MODE smaa_mode = SMAA_MODE.FINAL;
  
  // AA mode, selected with keys '1' - '4'
  enum AA_MODE{  NoAA, MSAA, SMAA, FXAA, GBAA }
  
  // SMAA mode, selected with keys 'q', 'w', 'e'
  enum SMAA_MODE{ EGDES, BLEND, FINAL }
  
  // render stuff
  PFont font;
  float gamma = 2.2f;
  float BACKGROUND_COLOR = 32;
  
  PShape shape;

  
  public void settings() {
    if(START_FULLSCREEN){
      viewport_w = displayWidth;
      viewport_h = displayHeight;
      viewport_x = 0;
      viewport_y = 0;
      fullScreen(P3D);
    } else {
      size(viewport_w, viewport_h, P3D);
    }
    smooth(0);
  }
  
  
  
  
  public void setup() {
    surface.setLocation(viewport_x, viewport_y);

    // camera
    peasycam = new PeasyCam(this, -4.083,  -6.096,   7.000, 2000);
    peasycam.setRotations(  1.085,  -0.477,   2.910);

    // projection
    perspective(60 * DEG_TO_RAD, width/(float)height, 2, 6000);
    
    // processing font
    font = createFont("Calibri", 48);
    textFont(font);
    
    // load obj file into shape-object
    shape = loadShape("examples/data/skylight_demo_scene.obj");
    shape.scale(20);

    
    // MSAA - main render-target for MSAA
    pg_render_msaa = (PGraphics3D) createGraphics(width, height, P3D);
    pg_render_msaa.smooth(8);
    pg_render_msaa.textureSampling(5);
    
    // NOAA - main render-target for FXAA and MSAA
    pg_render_noaa = (PGraphics3D) createGraphics(width, height, P3D);
    pg_render_noaa.smooth(0);
    pg_render_noaa.textureSampling(5);
    
    // FXAA
    pg_render_fxaa = (PGraphics3D) createGraphics(width, height, P3D);
    pg_render_fxaa.smooth(0);
    pg_render_fxaa.textureSampling(5);
    
    // SMAA
    pg_render_smaa = (PGraphics3D) createGraphics(width, height, P3D);
    pg_render_smaa.smooth(0);
    pg_render_smaa.textureSampling(5);
    
    // GBAA
    pg_render_gbaa = (PGraphics3D) createGraphics(width, height, P3D);
    pg_render_gbaa.smooth(0);
    pg_render_gbaa.textureSampling(5);
    
    // main library context
    context = new DwPixelFlow(this);
    
    // callback for scene display (used in GBAA)
    DwSceneDisplay scene_display = new DwSceneDisplay() {  
      @Override
      public void display(PGraphics3D canvas) {
        displayScene(canvas); 
      }
    };
    
    // AA post-processing modes
    fxaa = new FXAA(context);
    smaa = new SMAA(context);
    gbaa = new GBAA(context, scene_display);
    
    frameRate(1000);
  }


  

  public void draw() {
    if(aa_mode == AA_MODE.MSAA){
      displaySceneWrap(pg_render_msaa);
      // RGB gamma correction
      DwFilter.get(context).gamma.apply(pg_render_msaa, pg_render_msaa, gamma);
    }
    
    if(aa_mode == AA_MODE.NoAA || aa_mode == AA_MODE.SMAA || aa_mode == AA_MODE.FXAA){
      displaySceneWrap(pg_render_noaa);
      // RGB gamma correction
      DwFilter.get(context).gamma.apply(pg_render_noaa, pg_render_noaa, gamma);
    }
    
    if(aa_mode == AA_MODE.SMAA) smaa.apply(pg_render_noaa, pg_render_smaa);
    if(aa_mode == AA_MODE.FXAA) fxaa.apply(pg_render_noaa, pg_render_fxaa);
    
    // only for debugging
    if(aa_mode == AA_MODE.SMAA){
      if(smaa_mode == SMAA_MODE.EGDES) DwFilter.get(context).copy.apply(smaa.tex_edges, pg_render_smaa);
      if(smaa_mode == SMAA_MODE.BLEND) DwFilter.get(context).copy.apply(smaa.tex_blend, pg_render_smaa);
    }
    
    if(aa_mode == AA_MODE.GBAA){
      displaySceneWrap(pg_render_noaa);
      // RGB gamma correction
      DwFilter.get(context).gamma.apply(pg_render_noaa, pg_render_noaa, gamma);
      gbaa.apply(pg_render_noaa, pg_render_gbaa);
    }
    
    
    PGraphics3D display = pg_render_noaa;
    
    switch(aa_mode){
      case NoAA: display = pg_render_noaa; break;
      case MSAA: display = pg_render_msaa; break;
      case SMAA: display = pg_render_smaa; break;
      case FXAA: display = pg_render_fxaa; break;
      case GBAA: display = pg_render_gbaa; break;
    }


    peasycam.beginHUD();
    {
      // display Anti Aliased result
      blendMode(REPLACE);
      clear();
      image(display, 0, 0);
      blendMode(BLEND);
      
      float magnification = 4f;
      
      int sx = width / 20;
      int sy = sx;
      int mx = mouseX - sx / 2;
      int my = mouseY - sy / 2;
      PImage window = display.get(mx, my, sx, sy);
      
      strokeWeight(1);
      stroke(200,0,0,200);
      noFill();
      rect(mx, my, sx, sy);
      
      
      ((PGraphicsOpenGL)g).textureSampling(2);
      int mag_w = ceil(sx * magnification);
      int mag_h = mag_w;
      int mag_x = 0;
      int mag_y = height-mag_h;
      image(window, mag_x, mag_y, mag_w, mag_h);
      
      rect(mag_x, mag_y, mag_w, mag_h);
      
      // display AA name
      String mode = aa_mode.name();
      String buffer = "";
      if(aa_mode == AA_MODE.SMAA){
        if(smaa_mode == SMAA_MODE.EGDES ) buffer = " ["+smaa_mode.name()+"]";
        if(smaa_mode == SMAA_MODE.BLEND ) buffer = " ["+smaa_mode.name()+"]";
      }
      
      
      noStroke();
      fill(0,150);
      rect(0, height-65, mag_w, 65);
      
      
      int tx = 20;
      int ty = height-20;
      fill(0);
      text(mode + buffer, tx+2, ty+2);
      
      fill(255,200,0);
      text(mode + buffer, tx, ty);
      
    }
    peasycam.endHUD();

    // some info, window title
    String txt_fps = String.format(getClass().getName()+ "   [fps %6.2f]", frameRate);
    surface.setTitle(txt_fps);
  }


  
  public void displaySceneWrap(PGraphics3D canvas){
    canvas.beginDraw();
    DwGLTextureUtils.copyMatrices((PGraphics3D) this.g, canvas);
    float BACKGROUND_COLOR_GAMMA = (float) (Math.pow(BACKGROUND_COLOR/255.0, gamma) * 255.0);

    // background
    canvas.blendMode(PConstants.BLEND);
    canvas.background(BACKGROUND_COLOR_GAMMA);
    displayScene(canvas);
    canvas.endDraw();
  }
  
  

  // render something
  public void displayScene(PGraphics3D canvas){
    // lights
    canvas.directionalLight(255, 255, 255, 200,600,400);
    canvas.directionalLight(255, 255, 255, -200,-600,-400);
    canvas.ambientLight(64, 64, 64);
    
//    canvas.shape(shape);
    boxes(canvas);
  }
  
  
  
  public void boxes(PGraphics3D canvas){
    int num_boxes = 50;
    int num_spheres = 50;
    
    float bb_size = 800;
    float xmin = -bb_size;
    float xmax = +bb_size;
    float ymin = -bb_size;
    float ymax = +bb_size;
    float zmin =  0;
    float zmax = +bb_size;

    Random rand = new Random(0);

    canvas.colorMode(HSB, 360, 1, 1);
    canvas.noStroke();
//    canvas.stroke(0);
//    canvas.strokeWeight(1f);
    randomSeed(0);

    for(int i = 0; i < num_boxes; i++){
      float px = random(xmin, xmax);
      float py = random(ymin, ymax);
      float sx = random(200) + 10;
      float sy = random(200) + 10;
      float sz = random(zmin, zmax);

      float hsb_h = 0;
      float hsb_s = 0;
      float hsb_b = random(0.1f,1.0f);
      int shading = canvas.color(hsb_h, hsb_s, hsb_b);
      
//      if(random(0,1) > 0.5f){
////        canvas.noStroke();
//      } else {
////        canvas.stroke(0);
////        canvas.strokeWeight(1f);
//      }
      
      canvas.pushMatrix();
      canvas.translate(px, py, sz/2);
      canvas.fill(shading);
      canvas.box(sx,sy,sz);
      canvas.popMatrix();
    }
    
    DwCube cube_smooth = new DwCube(4);
    PShape shp_sphere_smooth = createShape(PShape.GEOMETRY);
    DwMeshUtils.createPolyhedronShape(shp_sphere_smooth, cube_smooth, 1, 4, true);
    
    DwCube cube_facets = new DwCube(2);
    PShape shp_sphere_facets = createShape(PShape.GEOMETRY);
    DwMeshUtils.createPolyhedronShape(shp_sphere_facets, cube_facets, 1, 4, false);

//    shp_sphere.setFill(color(210, 1,1));
    for(int i = 0; i < num_spheres; i++){
      float px = random(xmin, xmax);
      float py = random(ymin, ymax);
      float pz = random(zmin, zmax);
      float rr = random(50) + 50;
      
      
      boolean facets = (i%2 == 0);

      float hsb_h = (facets ? 210 : 90) + (float)(rand.nextFloat() - 0.5f) * 20 ;
      float hsb_s = (float) rand.nextFloat() * 0.99f + 0.01f;
      float hsb_b = random(0.6f,1.0f);
      int shading = canvas.color(hsb_h, hsb_s, hsb_b);
      
      PShape shp_sphere = facets ? shp_sphere_smooth : shp_sphere_facets;
      
      shp_sphere.setFill(shading);

      canvas.pushMatrix();
      canvas.translate(px, py, pz);
      canvas.scale(rr);
      canvas.shape(shp_sphere);
      canvas.popMatrix();
    }

    canvas.colorMode(RGB, 255, 255, 255);
    
//    PShape grid = createGridXY(30,40);
//    grid.setStroke(true);
//    grid.setStrokeWeight(1.0f);
//    grid.setStroke(color(164, 64, 0));
//    canvas.shape(grid);
    
    canvas.rectMode(CENTER);
    canvas.rect(0, 0, 2000, 2000);
  }
  
  
  
  public PShape createGridXY(int lines, float s){
    PShape shp_gridxy = createShape();
    shp_gridxy.beginShape(LINES);
    shp_gridxy.stroke(0);
    shp_gridxy.strokeWeight(1f);
    float d = lines*s;
    for(int i = 0; i <= lines; i++){
      shp_gridxy.vertex(-d,-i*s,0); shp_gridxy.vertex(d,-i*s,0);
      shp_gridxy.vertex(-d,+i*s,0); shp_gridxy.vertex(d,+i*s,0);
      
      shp_gridxy.vertex(-i*s,-d,0); shp_gridxy.vertex(-i*s,d,0);
      shp_gridxy.vertex(+i*s,-d,0); shp_gridxy.vertex(+i*s,d,0);
    }
    shp_gridxy.endShape();
    return shp_gridxy;
  }
  
  

  public void printCam(){
    float[] pos = peasycam.getPosition();
    float[] rot = peasycam.getRotations();
    float[] lat = peasycam.getLookAt();
    float   dis = (float) peasycam.getDistance();
    
    System.out.printf(Locale.ENGLISH, "position: (%7.3f, %7.3f, %7.3f)\n", pos[0], pos[1], pos[2]);
    System.out.printf(Locale.ENGLISH, "rotation: (%7.3f, %7.3f, %7.3f)\n", rot[0], rot[1], rot[2]);
    System.out.printf(Locale.ENGLISH, "look-at:  (%7.3f, %7.3f, %7.3f)\n", lat[0], lat[1], lat[2]);
    System.out.printf(Locale.ENGLISH, "distance: (%7.3f)\n", dis);
  }
  

  public void keyReleased(){
    if(key == 'c') printCam();
    
    if(key == '1') aa_mode = AA_MODE.NoAA;
    if(key == '2') aa_mode = AA_MODE.MSAA;
    if(key == '3') aa_mode = AA_MODE.SMAA;
    if(key == '4') aa_mode = AA_MODE.FXAA;
    if(key == '5') aa_mode = AA_MODE.GBAA;
    
    if(key == 'q') smaa_mode = SMAA_MODE.EGDES;
    if(key == 'w') smaa_mode = SMAA_MODE.BLEND;
    if(key == 'e') smaa_mode = SMAA_MODE.FINAL;
  }
  
  
  public static void main(String args[]) {
    PApplet.main(new String[] { AntiAliasing.class.getName() });
  }
}
