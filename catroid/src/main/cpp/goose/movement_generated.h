#pragma once
#include <stdint.h>
typedef struct gs_stack gs_stack;
#pragma pack(push, 1)
typedef struct { uint8_t *base; int64_t len; } gs_rhdr;
typedef struct { gs_rhdr *hdr; gs_stack *stk; } gs_rref;
typedef struct { gs_rhdr *hdr; gs_stack *stk; gs_rhdr *fl; gs_stack *flstk; } gs_pref;

typedef struct { float *data; int64_t len; } sl_f32;

typedef struct { uint8_t *data; int64_t len; } sl_u8;

#pragma pack(pop)

#ifdef __cplusplus
extern "C" {
#endif
void goose_init(int argc, char **argv);
int32_t newcatroid_goose_add(int32_t gs_export_arg0, int32_t gs_export_arg1);
float newcatroid_velocity_y(float gs_export_arg0_2, float gs_export_arg1_2, float gs_export_arg2);
float newcatroid_integrate(float gs_export_arg0_3, float gs_export_arg1_3, float gs_export_arg2_2);
void newcatroid_movement_batch(sl_f32 gs_export_arg0_4, float gs_export_arg1_4);
#ifdef __cplusplus
}
#endif
