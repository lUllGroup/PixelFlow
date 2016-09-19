/**
 * 
 * PixelFlow | Copyright (C) 2016 Thomas Diewald - http://thomasdiewald.com
 * 
 * A Processing/Java library for high performance GPU-Computing (GLSL).
 * MIT License: https://opensource.org/licenses/MIT
 * 
 */




package OpticalFlow_CaptureParticles_Verlet;



import com.thomasdiewald.pixelflow.java.CollisionGridAccelerator;
import com.thomasdiewald.pixelflow.java.OpticalFlow;
import com.thomasdiewald.pixelflow.java.PixelFlow;
import com.thomasdiewald.pixelflow.java.filter.Filter;
import com.thomasdiewald.pixelflow.java.verletPhysics2D.VerletParticle2D;

import controlP5.Accordion;

import controlP5.ControlP5;
import controlP5.Group;
import controlP5.RadioButton;
import controlP5.Toggle;
import processing.core.*;
import processing.opengl.PGraphics2D;
import processing.video.Capture;


public class OpticalFlow_CaptureParticles_Verlet extends PApplet {
 
  
  int cam_w = 640;
  int cam_h = 480;
  
  int view_w = 1200;
  int view_h = (int)(view_w * cam_h/(float)cam_w);
  int view_x = 230;
  int view_y = 0;
  
  int gui_w = 200;
  int gui_x = view_w;
  int gui_y = 0;
  
  //main library context
  PixelFlow context;
  
  // optical flow
  OpticalFlow opticalflow;
  
  // buffer for the capture-image
  PGraphics2D pg_cam_a, pg_cam_b; 

  // offscreen render-target
  PGraphics2D pg_oflow;
  
  // camera capture (video library)
  Capture cam;
  
  // some state variables for the GUI/display
  int     BACKGROUND_COLOR    = 0;
  boolean DISPLAY_SOURCE      = true;
  boolean APPLY_GRAYSCALE     = true;
  boolean APPLY_BILATERAL     = true;
  boolean COLLISION_DETECTION = true;
  
  // particle system, cpu
  public ParticleSystem particlesystem;
  
  // acceleration structure for collision detection
  public CollisionGridAccelerator collision_grid;
  

  public void settings() {
    size(view_w + gui_w, view_h, P2D);
    smooth(4);
  }

  public void setup() {

    surface.setLocation(view_x, view_y);
    
    // main library context
    context = new PixelFlow(this);
    context.print();
    context.printGL();
    
    // optical flow
    opticalflow = new OpticalFlow(context, cam_w, cam_h);
    
    // optical flow parameters
    opticalflow.param.display_mode = 3;
    
    cam = new Capture(this, cam_w, cam_h, 30);
    cam.start();
    
    pg_cam_a = (PGraphics2D) createGraphics(cam_w, cam_h, P2D);
    pg_cam_a.noSmooth();
    
    pg_cam_b = (PGraphics2D) createGraphics(cam_w, cam_h, P2D);
    pg_cam_b.noSmooth();
    
    pg_oflow = (PGraphics2D) createGraphics(view_w, view_h, P2D);
    pg_oflow.smooth(4);
    
    
    // particle system object
    particlesystem = new ParticleSystem(this, view_w, view_h);
    
    // set some parameters
    particlesystem.PARTICLE_COUNT              = 1000;
    particlesystem.PARTICLE_SCREEN_FILL_FACTOR = 0.70f;
    particlesystem.PARTICLE_SHAPE_IDX          = 0;

    particlesystem.MULT_FLUID                  = 0.40f;
    particlesystem.MULT_GRAVITY                = 0.50f;

    particlesystem.particle_param.BOUNDS_DAMPING    = 1f;
    particlesystem.particle_param.COLLISION_DAMPING = 0.80f;
    particlesystem.particle_param.VELOCITY_DAMPING  = 0.90f;
    
    
    particlesystem.initParticles();
    
    // collision detection accelerating system
    collision_grid = new CollisionGridAccelerator();

    createGUI();

    background(BACKGROUND_COLOR);
    frameRate(60);

  }
  


  
  // float buffer for pixel transfer from OpenGL to the host application
  float[] flow_velocity = new float[cam_w * cam_h * 2];

  public void draw() {
    
    if( cam.available() ){
      cam.read();
      
      // render to offscreenbuffer
      pg_cam_a.beginDraw();
      pg_cam_a.image(cam, 0, 0);
      pg_cam_a.endDraw();

      
      // apply filters (not necessary)
      if(APPLY_GRAYSCALE){
        Filter.get(context).luminance.apply(pg_cam_a, pg_cam_a);
      }
      if(APPLY_BILATERAL){
        Filter.get(context).bilateral.apply(pg_cam_a, pg_cam_b, 5, 0.10f, 4);
        swapCamBuffer();
      }
      
      // update Optical Flow
      opticalflow.update(pg_cam_a);
      
      
      // render Optical Flow
      pg_oflow.beginDraw();
      pg_oflow.background(BACKGROUND_COLOR);
      if(DISPLAY_SOURCE){
        pg_oflow.image(pg_cam_a, 0, 0, view_w, view_h);
      }
      pg_oflow.endDraw();

      // add flow-vectors to the image
      if(opticalflow.param.display_mode == 2){
        opticalflow.renderVelocityShading(pg_oflow);
      }
      opticalflow.renderVelocityStreams(pg_oflow, 10);
      
      
      // Transfer velocity data from the GPU to the host-application
      // This is in general a bad idea because such operations are very slow. So 
      // either do everything in shaders, and avoid memory transfer when possible, 
      // or do it very rarely. however, this is just an example for convenience.
      flow_velocity = opticalflow.getVelocity(flow_velocity);
       
    }
    
    
    
    // add a force to particle[0] with the middle mousebutton
    if(mousePressed && mouseButton == CENTER){
      VerletParticle2D particle = particlesystem.particles[0];
      float dx = mouseX - particle.cx;
      float dy = mouseY - particle.cy;
      
      float damping_pos = 0.3f;
      particle.cx  += dx * damping_pos;
      particle.cy  += dy * damping_pos;
    }
    
    
    

    // collision detection
    if(COLLISION_DETECTION && particlesystem.particle_param.COLLISION_DAMPING != 0.0){
      for(int i = 0; i < 1; i++){  
        collision_grid.updateCollisions(particlesystem.particles);
      }
      for (VerletParticle2D particle : particlesystem.particles) { 
        particle.updateBounds(0, 0, view_w, view_h);
      }
    }
    
    // update step: particle motion
    // 1) add fluid velocity to the particles' velocity
    // 2) add gravity
    // 3) update velocity + position + color
    for (VerletParticle2D particle : particlesystem.particles) {
      int px_view = Math.round(particle.cx);
      int py_view = Math.round(height - 1 - particle.cy); // invert y
      
      float scale_X = view_w / (float) cam_w;
      float scale_Y = view_h / (float) cam_h;
      
      int px_grid = (int)(px_view / scale_X);
      int py_grid = (int)(py_view / scale_Y);

      int w_grid  = opticalflow.frameCurr.velocity.w;

      int PIDX    = py_grid * w_grid + px_grid;

      float flow_vx = +flow_velocity[PIDX * 2 + 0] * particlesystem.MULT_FLUID;
      float flow_vy = -flow_velocity[PIDX * 2 + 1] * particlesystem.MULT_FLUID; // invert y
      
      particle.addForce(flow_vx, flow_vy);
      particle.addGravity(0, 0.05f * particlesystem.MULT_GRAVITY);
      particle.updatePosition(10, 10, width-10, height-10, 1);
      particle.updateShape();
    }
    
 
    // display result
    background(0);
    image(pg_oflow, 0, 0);
    
    
    // draw particlesystem
    PGraphics pg = this.g;
    pg.hint(DISABLE_DEPTH_MASK);
    pg.blendMode(BLEND);
//    pg.blendMode(ADD);
    particlesystem.display(pg);
    pg.blendMode(BLEND);

    // info
    String txt_fps = String.format(getClass().getName()+ "   [size %d/%d]   [frame %d]   [fps %6.2f]", cam_w, cam_h, opticalflow.UPDATE_STEP, frameRate);
    surface.setTitle(txt_fps);
   
  }
  
  
 
  void swapCamBuffer(){
    PGraphics2D tmp = pg_cam_a;
    pg_cam_a = pg_cam_b;
    pg_cam_b = tmp;
  }
  
 

  
  public void opticalFlow_setDisplayMode(int val){
    opticalflow.param.display_mode = val;
  }
  public void activeFilters(float[] val){
    APPLY_GRAYSCALE = (val[0] > 0);
    APPLY_BILATERAL = (val[1] > 0);
  }
  public void setOptionsGeneral(float[] val){
    DISPLAY_SOURCE = (val[0] > 0);
  }
 
  public void activateCollisionDetection(float[] val){
    COLLISION_DETECTION = (val[0] > 0);
  }

  
  
  ControlP5 cp5;
  
  public void createGUI(){
    
    cp5 = new ControlP5(this);
    
    int sx, sy, px, py, oy;
    
    sx = 100; sy = 14;
    oy = (int)(sy*1.5f);
    

    ////////////////////////////////////////////////////////////////////////////
    // GUI - OPTICAL FLOW
    ////////////////////////////////////////////////////////////////////////////
    Group group_oflow = cp5.addGroup("Optical Flow");
    {
      group_oflow.setSize(gui_w, 165).setHeight(20)
      .setBackgroundColor(color(16, 180)).setColorBackground(color(16, 180));
      group_oflow.getCaptionLabel().align(CENTER, CENTER);
      
      px = 10; py = 15;
      
      cp5.addSlider("blur input").setGroup(group_oflow).setSize(sx, sy).setPosition(px, py)
        .setRange(0, 30).setValue(opticalflow.param.blur_input).plugTo(opticalflow.param, "blur_input");
      
      cp5.addSlider("blur flow").setGroup(group_oflow).setSize(sx, sy).setPosition(px, py+=oy)
        .setRange(0, 10).setValue(opticalflow.param.blur_flow).plugTo(opticalflow.param, "blur_flow");
      
      cp5.addSlider("temporal smooth").setGroup(group_oflow).setSize(sx, sy).setPosition(px, py+=oy)
        .setRange(0, 1).setValue(opticalflow.param.temporal_smoothing).plugTo(opticalflow.param, "temporal_smoothing");
      
      cp5.addSlider("flow scale").setGroup(group_oflow).setSize(sx, sy).setPosition(px, py+=oy)
        .setRange(0, 200f).setValue(opticalflow.param.flow_scale).plugTo(opticalflow.param, "flow_scale");
  
      cp5.addSlider("threshold").setGroup(group_oflow).setSize(sx, sy).setPosition(px, py+=oy)
        .setRange(0, 3.0f).setValue(opticalflow.param.threshold).plugTo(opticalflow.param, "threshold");
      
      cp5.addRadio("opticalFlow_setDisplayMode").setGroup(group_oflow).setSize(18, 18).setPosition(px, py+=oy)
        .setSpacingColumn(40).setSpacingRow(2).setItemsPerRow(3)
        .addItem("dir"    , 0)
        .addItem("normal" , 1)
        .addItem("Shading", 2)
        .activate(opticalflow.param.display_mode);
    }
    
    
    
    ////////////////////////////////////////////////////////////////////////////
    // GUI - PARTICLES
    ////////////////////////////////////////////////////////////////////////////
    Group group_particles = cp5.addGroup("Particles");
    {
      
      group_particles.setHeight(20).setSize(gui_w, 260)
      .setBackgroundColor(color(16, 180)).setColorBackground(color(16, 180));
      group_particles.getCaptionLabel().align(CENTER, CENTER);
      
      sx = 100; px = 10; py = 10;oy = (int)(sy*1.4f);
      
      cp5.addButton("reset particles").setGroup(group_particles).setWidth(160).setPosition(10, 10).plugTo(particlesystem, "initParticles");

      cp5.addSlider("Particle count").setGroup(group_particles).setSize(sx, sy).setPosition(px, py+=oy+10)
          .setRange(10, 15000).setValue(particlesystem.PARTICLE_COUNT).plugTo(particlesystem, "setParticleCount");
      
      cp5.addSlider("Fill Factor").setGroup(group_particles).setSize(sx, sy).setPosition(px, py+=oy)
      .setRange(0.2f, 1.5f).setValue(particlesystem.PARTICLE_SCREEN_FILL_FACTOR).plugTo(particlesystem, "setFillFactor");
      
      cp5.addSlider("VELOCITY").setGroup(group_particles).setSize(sx, sy).setPosition(px, py+=oy+10)
          .setRange(0.85f, 1.0f).setValue(particlesystem.particle_param.VELOCITY_DAMPING).plugTo(particlesystem.particle_param, "VELOCITY_DAMPING");
      
      cp5.addSlider("GRAVITY").setGroup(group_particles).setSize(sx, sy).setPosition(px, py+=oy)
          .setRange(0, 10f).setValue(particlesystem.MULT_GRAVITY).plugTo(particlesystem, "MULT_GRAVITY");

      cp5.addSlider("FLOW").setGroup(group_particles).setSize(sx, sy).setPosition(px, py+=oy)
          .setRange(0, 1f).setValue(particlesystem.MULT_FLUID).plugTo(particlesystem, "MULT_FLUID");
      
      cp5.addSlider("SPRINGINESS").setGroup(group_particles).setSize(sx, sy).setPosition(px, py+=oy)
          .setRange(0, 1f).setValue(particlesystem.particle_param.COLLISION_DAMPING).plugTo(particlesystem.particle_param, "COLLISION_DAMPING");
      
      cp5.addCheckBox("activateCollisionDetection").setGroup(group_particles).setSize(40, 18).setPosition(px, py+=(int)(oy*1.5f))
          .setItemsPerRow(1).setSpacingColumn(3).setSpacingRow(3)
          .addItem("collision detection", 0)
          .activate(COLLISION_DETECTION ? 0 : 2);
      
      RadioButton rgb_shape = cp5.addRadio("setParticleShape").setGroup(group_particles).setSize(50, 18).setPosition(px, py+=(int)(oy*1.5f))
          .setSpacingColumn(2).setSpacingRow(2).setItemsPerRow(3).plugTo(particlesystem, "setParticleShape")
          .addItem("disk"  , 0)
          .addItem("spot"  , 1)
          .addItem("donut" , 2)
          .addItem("rect"  , 3)
          .addItem("circle", 4)
          .activate(particlesystem.PARTICLE_SHAPE_IDX);
      for(Toggle toggle : rgb_shape.getItems()) toggle.getCaptionLabel().alignX(CENTER);
    }
    
    
    
    
    
    
    ////////////////////////////////////////////////////////////////////////////
    // GUI - DISPLAY
    ////////////////////////////////////////////////////////////////////////////
    Group group_display = cp5.addGroup("display");
    {
      group_display.setHeight(20).setSize(gui_w, height)
      .setBackgroundColor(color(16, 180)).setColorBackground(color(16, 180));
      group_display.getCaptionLabel().align(CENTER, CENTER);
      
      px = 10; py = 15;
      
      cp5.addSlider("BACKGROUND").setGroup(group_display).setSize(sx,sy).setPosition(px, py)
          .setRange(0, 255).setValue(BACKGROUND_COLOR).plugTo(this, "BACKGROUND_COLOR");
  
      cp5.addCheckBox("setOptionsGeneral").setGroup(group_display).setSize(38, 18).setPosition(px, py+=oy)
          .setItemsPerRow(1).setSpacingColumn(3).setSpacingRow(3)
          .addItem("display source", 0).activate(DISPLAY_SOURCE ? 0 : 100);
  
      cp5.addCheckBox("activeFilters").setGroup(group_display).setSize(18, 18).setPosition(px, py+=(int)(oy*1.5f))
          .setItemsPerRow(1).setSpacingColumn(3).setSpacingRow(3)
          .addItem("grayscale"       , 0).activate(APPLY_GRAYSCALE ? 0 : 100)
          .addItem("bilateral filter", 1).activate(APPLY_BILATERAL ? 1 : 100);
    }
    
    
    ////////////////////////////////////////////////////////////////////////////
    // GUI - ACCORDION
    ////////////////////////////////////////////////////////////////////////////
    cp5.addAccordion("acc").setPosition(gui_x, gui_y).setWidth(gui_w).setSize(gui_w, height)
      .setCollapseMode(Accordion.MULTI)
      .addItem(group_oflow)
      .addItem(group_particles)
      .addItem(group_display)
      .open(0, 1, 2);
  }
  
  

  public static void main(String args[]) {
    PApplet.main(new String[] { OpticalFlow_CaptureParticles_Verlet.class.getName() });
  }
}