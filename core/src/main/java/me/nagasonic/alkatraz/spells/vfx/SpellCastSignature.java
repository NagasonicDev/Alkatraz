package me.nagasonic.alkatraz.spells.vfx;

import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;

/** Distinct, lightweight tells for the circle 7-9 spell roster. */
public final class SpellCastSignature {
    private SpellCastSignature() {}

    /** Draws one spell-specific frame. The base cast glyph remains the responsibility of the caller. */
    public static void draw(String id, LivingEntity caster, Location center, int frame,
                            double scale, Particle.DustOptions core, Particle.DustOptions accent) {
        if (id == null || caster == null || center == null || center.getWorld() == null) return;
        World world = center.getWorld();
        double phase = frame * 0.13;
        double r = Math.max(0.35, scale * 0.58);
        switch (id) {
            case "meteor_shower" -> { halo(caster, world, center, r, -0.65, 36, phase, core); rays(caster, world, center, r * 1.35, 7, -phase, accent); pillars(caster, world, center, r * 0.75, phase, core, 3); }
            case "radiant_brand" -> { halo(caster, world, center, r * 0.75, 0.65, 32, phase, core); rays(caster, world, center, r * 1.1, 5, phase, accent); }
            case "skeletal_knights" -> { fracture(caster, world, center, r, phase, core); pillars(caster, world, center, r * 0.65, -phase, accent, 4); }
            case "tectonic_strike" -> { fracture(caster, world, center, r * 1.15, phase, core); halo(caster, world, center, r * 0.55, -0.7, 24, -phase, accent); }
            case "thunderclap" -> { halo(caster, world, center, r * 1.2, -0.35, 40, phase, core); rays(caster, world, center, r, 8, -phase, accent); }
            case "undertow" -> { siphon(caster, world, center, r * 1.15, phase, core, accent); halo(caster, world, center, r * 0.5, -0.55, 28, -phase, accent); }
            case "warp" -> { portal(caster, world, center, r * 1.1, phase, core, accent); }
            case "apotheosis" -> { halo(caster, world, center, r, 2.5 + Math.sin(phase) * 0.25, 36, phase, core); rays(caster, world, center, r, 8, phase, accent); }
            case "arcane_resonance" -> { orbit(caster, world, center, r, 3, phase, core); orbit(caster, world, center, r * 0.58, 5, -phase * 1.7, accent); }
            case "choir_of_light" -> { halo(caster, world, center, r, 0.7, 32, phase, core); halo(caster, world, center, r * 0.72, 1.6, 32, -phase, accent); rays(caster, world, center, r * 0.9, 6, phase, core); }
            case "continental_shift" -> { fracture(caster, world, center, r, phase, core); fracture(caster, world, center, r * 0.55, -phase, accent); }
            case "crown_of_dawn" -> { halo(caster, world, center, r, 2.0, 40, phase, core); rays(caster, world, center, r * 1.15, 12, phase, accent); }
            case "cyclone_nest" -> { helix(caster, world, center, r, phase, core, accent, 3); }
            case "echo_cast" -> { halo(caster, world, center, r, 1.1, 28, phase, core); halo(caster, world, center, r * 0.68, 1.1, 28, phase - 1.2, accent); }
            case "everdrain" -> { siphon(caster, world, center, r, phase, core, accent); }
            case "fault_line" -> { fracture(caster, world, center, r * 1.15, phase, core); rays(caster, world, center, r * 0.6, 4, phase, accent); }
            case "inferno_pilgrimage" -> { helix(caster, world, center, r * 0.8, phase, core, accent, 2); rays(caster, world, center, r, 5, phase, accent); }
            case "inferno_rite" -> { halo(caster, world, center, r, 0.25, 36, phase, core); pillars(caster, world, center, r, phase, accent, 4); }
            case "oblivion" -> { collapse(caster, world, center, r, phase, core, accent); }
            case "stormcaller" -> { halo(caster, world, center, r, 0.2, 32, phase, core); rays(caster, world, center, r * 1.3, 7, phase, accent); }
            case "skywrath" -> { orbit(caster, world, center, r, 4, phase, core); rays(caster, world, center, r, 5, phase * 1.8, accent); }
            case "sunfire_barrage" -> { orbit(caster, world, center, r, 3, phase, accent); rays(caster, world, center, r * 1.2, 9, -phase, core); }
            case "thousand_waves" -> { halo(caster, world, center, r * (0.7 + 0.1 * Math.sin(phase)), 0.15, 36, phase, core); halo(caster, world, center, r * 0.45, 0.15, 24, -phase, accent); }
            case "tide_of_the_deep" -> { helix(caster, world, center, r, phase, core, accent, 2); halo(caster, world, center, r * 0.55, 0.3, 28, -phase, accent); }
            case "umbral_volley" -> { orbit(caster, world, center, r, 3, phase, core); rays(caster, world, center, r * 1.2, 3, phase, accent); }
            case "void_anchor" -> { halo(caster, world, center, r, 0.3, 32, phase, core); tether(caster, world, center, r, phase, accent); }
            case "waystone_recall" -> { portal(caster, world, center, r, phase, core, accent); }
            case "worldbreaker" -> { fracture(caster, world, center, r * 1.3, phase, core); fracture(caster, world, center, r * 0.75, -phase, accent); }
            default -> { }
        }
    }

    private static void halo(LivingEntity c, World w, Location p, double r, double y, int n, double a, Particle.DustOptions d) {
        Location q = p.clone();
        for (int i = 0; i < n; i++) { double t = a + i * Math.PI * 2 / n; q.setX(p.getX() + Math.cos(t) * r); q.setY(p.getY() + y + Math.sin(t) * r * 0.42); q.setZ(p.getZ() + Math.sin(t) * r); dust(c,w,q,d); }
    }
    private static void orbit(LivingEntity c, World w, Location p, double r, int count, double a, Particle.DustOptions d) {
        for (int k = 0; k < count; k++) { double t = a + k * Math.PI * 2 / count; Location q = p.clone().add(Math.cos(t) * r, Math.sin(t * 1.7) * 0.75, Math.sin(t) * r); dust(c,w,q,d); }
    }
    private static void rays(LivingEntity c, World w, Location p, double r, int count, double a, Particle.DustOptions d) {
        for (int k=0;k<count;k++) { double t=a+k*Math.PI*2/count; for(int j=0;j<4;j++){ double u=j/3.0; Location q=p.clone().add(Math.cos(t)*r*u, (u-0.5)*0.7, Math.sin(t)*r*u); dust(c,w,q,d); } }
    }
    private static void helix(LivingEntity c, World w, Location p, double r, double a, Particle.DustOptions d1, Particle.DustOptions d2, int strands) {
        for(int s=0;s<strands;s++) for(int i=0;i<16;i++){ double u=i/15.0,t=a+u*Math.PI*2+s*Math.PI*2/strands; Location q=p.clone().add(Math.cos(t)*r*u, (u-0.5)*2.0, Math.sin(t)*r*u); dust(c,w,q,(i%3==0)?d2:d1); }
    }
    private static void fracture(LivingEntity c, World w, Location p, double r, double a, Particle.DustOptions d) {
        for(int arm=0;arm<6;arm++){ double t=a+arm*Math.PI/3; for(int i=1;i<=7;i++){ double u=i/7.0; double zig=(i%2==0?0.10:-0.10)*r; Location q=p.clone().add(Math.cos(t)*r*u+Math.cos(t+Math.PI/2)*zig,-0.8,Math.sin(t)*r*u+Math.sin(t+Math.PI/2)*zig); dust(c,w,q,d); } }
    }
    private static void siphon(LivingEntity c, World w, Location p, double r, double a, Particle.DustOptions d1, Particle.DustOptions d2) {
        for(int i=0;i<18;i++){ double u=i/17.0,t=a+i*0.52; Location q=p.clone().add(Math.cos(t)*r*(1-u), (u-0.5)*1.8, Math.sin(t)*r*(1-u)); dust(c,w,q,i%4==0?d2:d1); }
    }
    private static void collapse(LivingEntity c, World w, Location p, double r, double a, Particle.DustOptions d1, Particle.DustOptions d2) {
        for(int ring=0;ring<3;ring++){ double rr=r*(1-ring*0.24); for(int i=0;i<20;i++){ double t=a*(ring%2==0?1:-1)+i*Math.PI*2/20; Location q=p.clone().add(Math.cos(t)*rr,Math.sin(t*2+ring)*0.32,Math.sin(t)*rr); dust(c,w,q,ring==1?d2:d1); } }
    }
    private static void pillars(LivingEntity c, World w, Location p, double r, double a, Particle.DustOptions d, int n) {
        for(int k=0;k<n;k++){ double t=a+k*Math.PI*2/n,x=Math.cos(t)*r,z=Math.sin(t)*r; for(int i=0;i<8;i++){ Location q=p.clone().add(x,(i/7.0-0.5)*2,z); dust(c,w,q,d); } }
    }
    private static void tether(LivingEntity c, World w, Location p, double r, double a, Particle.DustOptions d) {
        for(int i=0;i<15;i++){ double u=i/14.0,t=a+u*1.8; Location q=p.clone().add(Math.cos(t)*r*(1-u*0.8),(u-0.5)*2,Math.sin(t)*r*(1-u*0.8)); dust(c,w,q,d); }
    }
    private static void portal(LivingEntity c, World w, Location p, double r, double a, Particle.DustOptions d1, Particle.DustOptions d2) {
        double yaw = Math.toRadians(c.getLocation().getYaw());
        double rightX = Math.cos(yaw), rightZ = Math.sin(yaw);
        for(int i=0;i<36;i++){ double t=i*Math.PI*2/36; Location q=p.clone().add(rightX*Math.cos(t)*r,Math.sin(t)*r*1.35,rightZ*Math.cos(t)*r); dust(c,w,q,i%4==0?d2:d1); }
        for(int i=0;i<8;i++){ double t=a+i*Math.PI/4; Location q=p.clone().add(rightX*Math.cos(t)*r*0.65,Math.sin(t)*r,rightZ*Math.cos(t)*r*0.65); dust(c,w,q,d2); }
    }
    private static void dust(LivingEntity c, World w, Location p, Particle.DustOptions d) { ParticleCaster.spawn(c,w,Utils.DUST,p,0,d); }
}
