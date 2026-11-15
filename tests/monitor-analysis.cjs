const assert=require('node:assert/strict');
const {analyze}=require('../app/src/main/assets/monitor-analysis.js');
let assertions=0;function check(test){test();assertions++;}
function image(w,h,color){const a=new Uint8ClampedArray(w*h*4);for(let i=0;i<a.length;i+=4)a.set([...color,255],i);return a;}
for(const [color,bin] of [[[0,0,0],0],[[255,255,255],255],[[255,0,0],77],[[0,255,0],149],[[0,0,255],29]]){
 const r=analyze(image(16,16,color),16,16,true);check(()=>assert.equal(r.histogram[bin],256));check(()=>assert.equal(r.marked,0));check(()=>assert.equal(r.histogram.reduce((a,b)=>a+b,0),256));
}
const edge=image(32,24,[0,0,0]);for(let y=0;y<24;y++)for(let x=16;x<32;x++)edge.set([255,255,255,255],(y*32+x)*4);
const r=analyze(edge,32,24,true);check(()=>assert.equal(r.marked,44));check(()=>assert.equal(r.histogram[0],384));check(()=>assert.equal(r.histogram[255],384));
check(()=>assert.equal(r.mask[(12*32+15)*4+3],215));check(()=>assert.equal(r.mask[(12*32+10)*4+3],0));
check(()=>assert.equal(analyze(edge,32,24,false).marked,0));
check(()=>assert.throws(()=>analyze(edge,321,24,true)));check(()=>assert.throws(()=>analyze(edge,32,23,true)));check(()=>assert.throws(()=>analyze(edge,0,24,true)));
console.log(`PASS: ${assertions} JPEG histogram / Sobel peaking assertions (synthetic pixels; not optical focus accuracy)`);
