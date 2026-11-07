// paketlicht.glsl — das Licht von LituyaBay1958 für die Shader der Packages aus Renderer.jar, die einen Lichtvertrag
// haben (com.dan.surge: SurgeGl.hostLight und surge_surface.glsl; com.dan.forest: TreeFallGl.hostLight). Der Vertrag
// verlangt lightSun, lightSky, lightSkyDir, lightHaze, lightAerial, lightIrradiance und die Uniforms uVP, uCam, uSun,
// uTime, uLevel, uFrame; hier alles aus common.glsl: Sonne am Punkt mit Bergschatten (com.dan.horizon) und Wolkenschatten,
// Rundumbild aus com.dan.sky, Luftperspektive in zwei Schichten. Die Uniforms setzt Renderer.common.
#include "common.glsl"
#define lightSun sunAt
#define lightSky umgebung
#define lightSkyDir skyColor
#define lightHaze hazeColor
#define lightAerial aerial
#define uFrame uBild
vec3 lightIrradiance() { return uSkyMode == 1 ? uSkyAmb * 3.14159 : skyColor(vec3(0.0, 1.0, 0.0)) * 0.6; }
