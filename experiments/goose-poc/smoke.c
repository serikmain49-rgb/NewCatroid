#include "goose_poc.h"
#include <stdio.h>

int main(int argc, char **argv) {
    goose_init(argc, argv);
    if (newcatroid_goose_add(20, 22) != 42 || newcatroid_goose_add(-2, 3) != 1) {
        fputs("Goose smoke test failed\n", stderr);
        return 1;
    }
    puts("Goose C ABI smoke test passed");
    return 0;
}
