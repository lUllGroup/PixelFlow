/**
 * 
 * PixelFlow | Copyright (C) 2016 Thomas Diewald - http://thomasdiewald.com
 * 
 * A Processing/Java library for high performance GPU-Computing (GLSL).
 * MIT License: https://opensource.org/licenses/MIT
 * 
 */

package com.thomasdiewald.pixelflow.java.particlephysics;


import java.util.Arrays;

import com.thomasdiewald.pixelflow.java.accelerationstructures.DwCollisionObject;

import processing.core.PMatrix3D;
import processing.core.PShape;


public class DwParticle3D implements DwCollisionObject{
  
  // max radius among all particles, can be used for normalization, ...
  static public float MAX_RAD = 0; 
  
  static public class Param{
    public float DAMP_BOUNDS    = 1;
    public float DAMP_COLLISION = 1;
    public float DAMP_VELOCITY  = 1;
  }
  
  Param param = new Param();

  
  // index (must be unique)
  // must match the position of the array, for indexing
  public int idx;
  
  
  // pinned to a position
  public boolean enable_collisions = true;
  public boolean enable_springs    = true;
  public boolean enable_forces     = true;
  

    // physics: verlet integration
  public float cx = 0, cy = 0, cz = 0; // current position
  public float px = 0, py = 0, pz = 0; // previous position
  public float ax = 0, ay = 0, az = 0; // acceleration
  public float rad  = 0;           // radius
  public float rad_collision  = 0; // collision radius
  public float mass = 1f;          // mass

  
  // Spring Constraints
  public int spring_count = 0;
  public DwSpringConstraint3D[] springs = null;
  public boolean all_springs_deactivated = false;
  
  
  // don'd apply collision on particles within the same group
  public int collision_group;
  public int collision_count;

  
  // display shape
  protected PShape    shp_particle  = null;
  protected PMatrix3D shp_transform = null;
  

  public DwParticle3D(int idx) {
    this.idx = idx;
    this.collision_group = idx;
  }
  public DwParticle3D(int idx, float x, float y, float z, float rad) {
    this(idx);
    setPosition(x, y, z);
    setRadius(rad);
  }
  public DwParticle3D(int idx, float x, float y, float z, float rad, Param param) {
    this(idx);
    setPosition(x, y, z);
    setRadius(rad);
    setParamByRef(param);
  }
  public void setPosition(float x, float y, float z){
    this.cx = this.px = x;
    this.cy = this.py = y;
    this.cz = this.pz = z;
  }
  
  public void setRadius(float rad_){
    rad = Math.max(rad_, 0.1f);
    rad_collision = rad;
  }
  public void setRadiusCollision(float rad_collision_){
    rad_collision = Math.max(rad_collision_, 0.1f);
  }
  
  public void setMass(float mass){
    this.mass = mass;
  }
  
  public void setParamByRef(Param param){
    if(param != null){
      this.param = param;
    }
  }
  
  public void setCollisionGroup(int id){
    collision_group = id;
  }
  
  public void enableCollisions(boolean enable_collisions){
    this.enable_collisions = enable_collisions;
  }
  
  public void enableSprings(boolean enable_springs){
    this.enable_springs = enable_springs;
  }
  
  public void enableForces(boolean enable_forces){
    this.enable_forces = enable_forces;
  }
  
  public void enable(boolean enable_collisions, boolean enable_springs, boolean enable_forces){
    this.enable_collisions = enable_collisions;
    this.enable_springs = enable_springs;
    this.enable_forces = enable_forces;
  }
  
  


  protected void addSpring(DwSpringConstraint3D spring){

    // make sure we don't have multiple springs to the same vertex.
    int pos = 0;
    while(pos < spring_count && springs[pos].pb.idx <= spring.pb.idx) pos++;
    
    // already in the list, so return
    if(pos > 0 && springs[pos-1].pb == spring.pb) return;
    
    // realloc if necessary
    if(springs == null || spring_count >= springs.length){
      int new_len = (int) Math.max(2, Math.ceil(spring_count*1.5f) );
      if( springs == null){
        springs = new DwSpringConstraint3D[new_len];
      } else {
        springs = Arrays.copyOf(springs, new_len);
      }
    }
    
    // shift data to the right, by one
    System.arraycopy(springs, pos, springs, pos+1, spring_count-pos);
    springs[pos] = spring;
    spring_count++;

    // check correct sorting
//    for(int i = 1; i < spring_count; i++){
//      if( springs[i].pb.idx <  springs[i-1].pb.idx) System.out.println("ERROR");
//    }
  }
  
  protected DwSpringConstraint3D removeSpring(DwParticle3D pb){
    DwSpringConstraint3D removed = null;
    int pos = 0;
    for(pos = 0; pos < spring_count; pos++){
      if(springs[pos].pb == pb){
        removed = springs[pos];
        break;
      }
    }
    if(removed != null){
      System.arraycopy(springs, pos+1, springs, pos, spring_count-(pos+1));
      spring_count--;
    }
    return removed;
  }
  
  

  



  
  
  
  
  
  
  
  //////////////////////////////////////////////////////////////////////////////
  // VERLET INTEGRATION
  //////////////////////////////////////////////////////////////////////////////
  public void moveTo(float cx_new, float cy_new, float cz_new, float damping){
    px  = cx;
    py  = cy;
    pz  = cz;
    cx += (cx_new - cx) * damping;
    cy += (cy_new - cy) * damping;
    cz += (cz_new - cz) * damping;
  }
  
  public void moveTo(float[] cnew, float damping){
    px  = cx;
    py  = cy;
    pz  = cz;
    cx += (cnew[0] - cx) * damping;
    cy += (cnew[1] - cy) * damping;
    cz += (cnew[2] - cz) * damping;
  }
  
  
  public void addForce(float ax, float ay, float az){
    this.ax += ax / mass;
    this.ay += ay / mass;
    this.az += az / mass;
  }
  
  public void addGravity(float gx, float gy, float gz){
    this.ax += gx;
    this.ay += gy;
    this.az += gz;
  }
  
  
  public void updatePosition(float xmin, float ymin, float zmin, float xmax, float ymax, float zmax, float timestep) {
    if(enable_forces){
      // velocity
      float vx = (cx - px) * param.DAMP_VELOCITY;
      float vy = (cy - py) * param.DAMP_VELOCITY;
      float vz = (cz - pz) * param.DAMP_VELOCITY;
      
      px = cx;
      py = cy;
      pz = cz;
  
      // verlet integration
      cx += vx + ax * 0.5 * timestep * timestep;
      cy += vy + ay * 0.5 * timestep * timestep;
      cz += vz + az * 0.5 * timestep * timestep;
      
      // constrain bounds
      updateBounds(xmin, ymin, zmin, xmax, ymax, zmax);
    }
    ax = ay = az = 0;
  }

  
  
  //////////////////////////////////////////////////////////////////////////////
  // SPRING CONSTRAINT
  //////////////////////////////////////////////////////////////////////////////

  
  public void updateSprings(DwParticle3D[] particles){
    // sum up force of attached springs
    DwParticle3D pa = this;
    for(int i = 0; i < spring_count; i++){
      DwSpringConstraint3D spring = springs[i];
      if(!spring.is_the_good_one) continue;
      DwParticle3D pb = spring.pb;
      
      float dx = pb.cx - pa.cx;
      float dy = pb.cy - pa.cy;
      float dz = pb.cz - pa.cz;
      
      float force = spring.updateForce();
      
      float pa_mass_factor = 2f * pb.mass / (pa.mass + pb.mass);
      float pb_mass_factor = 2f - pa_mass_factor;

      // 1) CPU-Version: converges much faster (on the CPU, of course)
      if(pa.enable_springs){
        pa.cx -= dx * force * pa_mass_factor;
        pa.cy -= dy * force * pa_mass_factor;  
        pa.cz -= dz * force * pa_mass_factor;  
      } 
      if(pb.enable_springs){
        pb.cx += dx * force * pb_mass_factor;
        pb.cy += dy * force * pb_mass_factor; 
        pb.cz += dz * force * pb_mass_factor; 
      }

      // 2) GPU-Version: converges slower, but result is more accurate
      // >>>>   just used for debugging   <<<<
      // this requires, to have bidirectional springs
      // --> SpringConstraint.makeAllSpringsBidirectional(particles);
//      if(pa.enable_springs){
//        pa.spring_x -= dx * force * pa_mass_factor;
//        pa.spring_y -= dy * force * pa_mass_factor;  
//        pa.spring_z -= dz * force * pa_mass_factor;  
//      }
    }
  }
  

  // spring force
  public float spring_x = 0;
  public float spring_y = 0;
  public float spring_z = 0;
  
  public void beforeSprings(){
    spring_x = spring_y = spring_z = 0;
  }
  public void afterSprings(float xmin, float ymin, float zmin, float xmax, float ymax, float zmax){
//    if(spring_count > 0){
//      cx += spring_x / spring_count;
//      cy += spring_y / spring_count;
//      cz += spring_z / spring_count;
//    }
    updateBounds(xmin, ymin, zmin, xmax, ymax, zmax);
  }
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  

  
  
  //////////////////////////////////////////////////////////////////////////////
  // PARTICLE COLLISION
  //////////////////////////////////////////////////////////////////////////////
  public void updateCollision(DwParticle3D othr) {

    if(!enable_collisions) return;
    if(othr.collision_group == this.collision_group) return; // particles are of the same group
    if(this == othr              ) return; // not colliding with myself
    if(this == othr.collision_ptr) return; // already collided with "othr"
    
    othr.collision_ptr = this; // mark as checked
      
    float dx        = othr.cx - this.cx;
    float dy        = othr.cy - this.cy;
    float dz        = othr.cz - this.cz;
    float dd_cur_sq = dx*dx + dy*dy + dz*dz;
    float dd_min    = othr.rad_collision + this.rad_collision;
    float dd_min_sq = dd_min*dd_min;
    
    if (dd_cur_sq < dd_min_sq) { 
      float this_mass_factor = 2f * othr.mass / (this.mass + othr.mass);
//      float othr_mass_factor = 2f - this_mass_factor;
      
//      float dd_cur     = (float) Math.sqrt(dd_cur_sq);
//      float force      = (0.5f * (dd_min - dd_cur) / (dd_cur + 0.00001f)) * param.DAMP_COLLISION;
//      this.collision_x = dx * force * this_mass_factor;
//      this.collision_y = dy * force * this_mass_factor;
//      this.collision_z = dz * force * this_mass_factor;
      
      // http://www.gotoandplay.it/_articles/2005/08/advCharPhysics.php
      float force   = (dd_min_sq / (dd_cur_sq + dd_min_sq) - 0.5f) * param.DAMP_COLLISION;

      this.collision_x -= dx * force * this_mass_factor;
      this.collision_y -= dy * force * this_mass_factor;
      this.collision_z -= dz * force * this_mass_factor;
      
      this.collision_count++;
//      if(this.enable_collisions)
//      {
//        this.cx -= dx * force * this_mass_factor;
//        this.cy -= dy * force * this_mass_factor;
//        this.cz -= dz * force * this_mass_factor;
//      }
//      if(othr.enable_collisions)
//      {
//        othr.cx += dx * force * othr_mass_factor;
//        othr.cy += dy * force * othr_mass_factor;
//        othr.cz += dz * force * othr_mass_factor;
//      }
    }
  }
  

  
  // collision force
  private float collision_x;
  private float collision_y;
  private float collision_z;
  
  public void beforeCollision(){
    collision_x = collision_y = collision_z = 0;
    collision_count = 0;
  }
  public void afterCollision(float xmin, float ymin, float zmin, float xmax, float ymax, float zmax){
//    if(collision_count == 0) return;
    // prevent explosions
    float limit = 1f;
//    float dd_sq = collision_x*collision_x + collision_y*collision_y + collision_z*collision_z;
//    float dd_max = rad/(float)(collision_count);
//
//    if( dd_sq > dd_max*dd_max){
//      limit = dd_max / (float) Math.sqrt(dd_sq);
//    }
    
    cx += collision_x * limit;
    cy += collision_y * limit;
    cz += collision_z * limit;
    updateBounds(xmin, ymin, zmin, xmax, ymax, zmax);
  }
  
  
  
  
  
  
  
  //////////////////////////////////////////////////////////////////////////////
  // BOUNDARY COLLISION
  //////////////////////////////////////////////////////////////////////////////
  public void updateBounds(float xmin, float ymin, float zmin, float xmax, float ymax, float zmax){
    if(!enable_collisions) return;
    float vx, vy, vz;
    float damping = param.DAMP_BOUNDS;
    if ((cx - rad) < xmin) {vx=cx-px;vy=cy-py;vz=cz-pz; cx=xmin+rad;px=cx+vx*damping;py=cy-vy*damping;pz=cz-vz*damping;}
    if ((cx + rad) > xmax) {vx=cx-px;vy=cy-py;vz=cz-pz; cx=xmax-rad;px=cx+vx*damping;py=cy-vy*damping;pz=cz-vz*damping;}
    if ((cy - rad) < ymin) {vx=cx-px;vy=cy-py;vz=cz-pz; cy=ymin+rad;px=cx-vx*damping;py=cy+vy*damping;pz=cz-vz*damping;}
    if ((cy + rad) > ymax) {vx=cx-px;vy=cy-py;vz=cz-pz; cy=ymax-rad;px=cx-vx*damping;py=cy+vy*damping;pz=cz-vz*damping;}
    if ((cz - rad) < zmin) {vx=cx-px;vy=cy-py;vz=cz-pz; cz=zmin+rad;px=cx-vx*damping;py=cy-vy*damping;pz=cz+vz*damping;}
    if ((cz + rad) > zmax) {vx=cx-px;vy=cy-py;vz=cz-pz; cz=zmax-rad;px=cx-vx*damping;py=cy-vy*damping;pz=cz+vz*damping; }
  }
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  private DwParticle3D collision_ptr = null;

  @Override
  public final void resetCollisionPtr() {
    collision_ptr = null;
  }
  
  @Override
  public final void update(DwCollisionObject othr) {
    updateCollision((DwParticle3D)othr);
  }

  @Override
  public final float x() {
    return cx;
  }

  @Override
  public final float y() {
    return cy;
  }
  
  @Override
  public final float z() {
    return cz;
  }


  @Override
  public final float rad() {
    return rad;

  }
  
  @Override
  public final float radCollision() {
    return rad_collision;
  }

  
  
  
  
  
  
  
  
  
  
  
  
  
  
  //////////////////////////////////////////////////////////////////////////////
  // DISPLAY
  //////////////////////////////////////////////////////////////////////////////
  public void setShape(PShape shape){
    shp_particle = shape;
    shp_transform = new PMatrix3D();
    updateShapePosition();
  }

  public void setColor(int col_argb){
    if(shp_particle != null){
      shp_particle.setTint(col_argb);
      shp_particle.setFill(col_argb);
    }
  }
  
  public void updateShape(){
    updateShapePosition();
    updateShapeColor();
  }
  
  public void updateShapePosition(){
    // build transformation matrix
    if(shp_transform != null){
      shp_transform.reset();
      shp_transform.translate(cx, cy, cz);
//      shp_transform.rotate((float)Math.atan2(cy-py, cx-px));
    }

    // update shape position
    if(shp_particle != null){
      shp_particle.resetMatrix();
      shp_particle.applyMatrix(shp_transform);
    }
  }
  
  protected final float[][] PALLETTE = 
    {
    {  50,  80,  130},    
    { 100, 178, 255}, 
    { 255, 120,  50},
//      {   25,    100,    255}, 
//      {   255,    0,    100}, 
  };

  protected final void getShading(float val, float[] rgb){
    if(val < 0.0) val = 0.0f; else if(val >= 1.0) val = 0.99999f;
    float lum_steps = val * (PALLETTE.length-1);
    int   idx = (int)(Math.floor(lum_steps));
    float fract = lum_steps - idx;
    
    rgb[0] = PALLETTE[idx][0] * (1-fract) +  PALLETTE[idx+1][0] * fract;
    rgb[1] = PALLETTE[idx][1] * (1-fract) +  PALLETTE[idx+1][1] * fract;
    rgb[2] = PALLETTE[idx][2] * (1-fract) +  PALLETTE[idx+1][2] * fract;
  }

  
  protected int clamp(float v){
    if( v <   0 ) return 0;
    if( v > 255 ) return 255;
    return (int)v;
  }
  
  private final float[] rgb = new float[3];
  
  public void updateShapeColor(){
    float vel  = getVelocity();
    float radn = 1.1f * rad / MAX_RAD;

    getShading(vel, rgb);
    int a = 255;
    int r = clamp(rgb[0] * radn) & 0xFF;
    int g = clamp(rgb[1] * radn) & 0xFF;
    int b = clamp(rgb[2] * radn) & 0xFF;
    
    int col = a << 24 | r << 16 | g << 8 | b;
    setColor(col);
  }
  
  public float getVelocity(){
    float vx = cx - px;
    float vy = cy - py;
    float vz = cz - pz;
    return (float) Math.sqrt(vx*vx + vy*vy + vz*vz);
  }
  
  
  
  

  
  
  
  
  
  
//  // TODO, move to some Utils class
//  static public int cross(VerletParticle3D p, VerletParticle3D pA, VerletParticle3D pB, float[] cross){
//    if(pA == null || pA.all_springs_deactivated ||
//       pB == null || pB.all_springs_deactivated)
//    {
//      cross[0] = cross[1] = cross[2] = 0;
//      return 0;
//    } else {
//      float dxA = pA.cx - p.cx;
//      float dyA = pA.cy - p.cy;
//      float dzA = pA.cz - p.cz;
//      
//      float dxB = pB.cx - p.cx;
//      float dyB = pB.cy - p.cy;
//      float dzB = pB.cz - p.cz;
//      
//      cross[0] = (dyA * dzB) - (dyB * dzA);
//      cross[1] = (dzA * dxB) - (dzB * dxA);
//      cross[2] = (dxA * dyB) - (dxB * dyA);
//      return 1;
//    }
//  }
  
  // TODO, move to some Utils class
  static public int crossAccum(DwParticle3D p, DwParticle3D pA, DwParticle3D pB, float[] cross){
    if(pA == null || pA.all_springs_deactivated ||
       pB == null || pB.all_springs_deactivated)
    {
      return 0;
    } else {
      float dxA = pA.cx - p.cx;
      float dyA = pA.cy - p.cy;
      float dzA = pA.cz - p.cz;
      
      float dxB = pB.cx - p.cx;
      float dyB = pB.cy - p.cy;
      float dzB = pB.cz - p.cz;
      
      cross[0] += (dyA * dzB) - (dyB * dzA);
      cross[1] += (dzA * dxB) - (dzB * dxA);
      cross[2] += (dxA * dyB) - (dxB * dyA);
      return 1;
    }
  }
  
  
}