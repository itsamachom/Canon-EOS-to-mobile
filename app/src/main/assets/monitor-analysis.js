/* Original monitor analysis. No Magic Lantern code or camera RAW data used. */
(function(root){
 'use strict';
 function analyze(rgba,width,height,edges){
  if(!Number.isInteger(width)||!Number.isInteger(height)||width<1||height<1||width>320||height>320||rgba.length!==width*height*4)throw new Error('Invalid analysis frame');
  const count=width*height,luma=new Uint8Array(count),histogram=new Uint32Array(256),mask=new Uint8ClampedArray(count*4);
  for(let i=0;i<count;i++){const p=i*4,y=(77*rgba[p]+150*rgba[p+1]+29*rgba[p+2]+128)>>8;luma[i]=y;histogram[y]++;}
  let marked=0;
  // Sobel on downsampled JPEG luminance. Fixed floor rejects flat/low-contrast noise.
  if(edges)for(let y=1;y<height-1;y++)for(let x=1;x<width-1;x++){
   const i=y*width+x,a=i-width,b=i+width;
   const gx=-luma[a-1]+luma[a+1]-2*luma[i-1]+2*luma[i+1]-luma[b-1]+luma[b+1];
   const gy=-luma[a-1]-2*luma[a]-luma[a+1]+luma[b-1]+2*luma[b]+luma[b+1];
   if(Math.abs(gx)+Math.abs(gy)>=220){const p=i*4;mask[p]=255;mask[p+1]=65;mask[p+2]=40;mask[p+3]=215;marked++;}
  }
  return {histogram,mask,marked};
 }
 const api={analyze};if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.MonitorAnalysis=api;
})(typeof globalThis!=='undefined'?globalThis:this);
