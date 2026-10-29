#define _GNU_SOURCE
#include <signal.h>
#include <stdio.h>
#include "movement_generated.h"

static void sentinel(int sig) { (void)sig; }

int main(void) {
    struct sigaction installed = {0}, before_segv, before_bus, after_segv, after_bus;
    stack_t before_stack, after_stack;
    installed.sa_handler = sentinel;
    sigemptyset(&installed.sa_mask);
    if (sigaction(SIGSEGV, &installed, NULL) || sigaction(SIGBUS, &installed, NULL)
            || sigaction(SIGSEGV, NULL, &before_segv) || sigaction(SIGBUS, NULL, &before_bus)
            || sigaltstack(NULL, &before_stack)) return 1;
    goose_init(0, NULL);
    if (sigaction(SIGSEGV, NULL, &after_segv) || sigaction(SIGBUS, NULL, &after_bus)
            || sigaltstack(NULL, &after_stack)) return 1;
    if (after_segv.sa_handler != before_segv.sa_handler || after_segv.sa_flags != before_segv.sa_flags
            || after_bus.sa_handler != before_bus.sa_handler || after_bus.sa_flags != before_bus.sa_flags
            || after_stack.ss_flags != before_stack.ss_flags || after_stack.ss_sp != before_stack.ss_sp
            || after_stack.ss_size != before_stack.ss_size) {
        fputs("Embedded Goose changed host signal handlers or alternate stack\n", stderr);
        return 1;
    }
    if (newcatroid_goose_add(20, 22) != 42) return 1;
    puts("PASS: Goose embedded init preserves host signal handlers and alternate stack");
    return 0;
}
