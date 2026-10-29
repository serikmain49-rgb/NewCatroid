#!/usr/bin/env python3
"""Adapt the pinned, allocation-free Goose module for embedding in Android.

This is deliberately tied to the generated runtime shape and zero Goose data
stacks. A compiler update or a module requiring stacks fails generation rather
than silently removing its required fault handlers.
"""
from pathlib import Path
import sys

path = Path(sys.argv[1])
code = path.read_text()
if code.count('gs_rt_init(argc, argv, 0, 0, 0);') != 1:
    raise SystemExit('Embedded Goose adapter requires exactly zero main/worker data stacks')
start = '''    gs_regions_init();
    gs_regions_begin(mainregions);
    gs_native_stack_init();
    gs_scan_init();'''
replacement = '''#ifdef NC_GOOSE_PURE_EMBED
    /* The generated module declares no data stacks or Goose worker stacks.
       Do not replace the host JVM's process-wide SIGSEGV/SIGBUS handlers. */
    if (mainregions || workerregions) gs_panic("pure Goose adapter cannot own data stacks");
    gs_page_size = gs_os_page_size();
#else
    gs_regions_init();
#endif
    gs_regions_begin(mainregions);
#ifndef NC_GOOSE_PURE_EMBED
    gs_native_stack_init();
#endif
    gs_scan_init();'''
if code.count(start) != 1:
    raise SystemExit('Pinned Goose runtime init block changed; review adapter')
code = code.replace(start, replacement)
stdout = '    setvbuf(stdout, NULL, _IONBF, 0);'
if code.count(stdout) != 1:
    raise SystemExit('Pinned Goose stdout initialization changed; review adapter')
code = code.replace(stdout, '#ifndef NC_GOOSE_PURE_EMBED\n' + stdout + '\n#endif')
path.write_text(code)
