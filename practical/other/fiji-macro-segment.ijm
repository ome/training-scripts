run("8-bit");
run("Invert", "stack");
setAutoThreshold("MaxEntropy");
run("Analyze Particles...", "size=10-Infinity pixel display clear add stack");

