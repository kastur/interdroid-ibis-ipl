#ifndef __IBIS_MANTA_IBIS_IMPL_MESSAGEPASSING_IBP_BYTE_STREAM_H__
#define __IBIS_MANTA_IBIS_IMPL_MESSAGEPASSING_IBP_BYTE_STREAM_H__

/* Native methods for ibis.impl.messagePassing.ByteOutputStream
 */

#include <jni.h>

extern int	ibmp_byte_stream_proto_start;

typedef struct IBP_BYTE_STREAM_HDR ibmp_byte_stream_hdr_t, *ibmp_byte_stream_hdr_p;

struct IBP_BYTE_STREAM_HDR {
    jint	dest_port;
    jint	src_port;
    jint	msgSeqno;
};

#define ibmp_byte_stream_hdr(proto) \
    (ibmp_byte_stream_hdr_p)((char *)proto + ibmp_byte_stream_proto_start)

void ibmp_byte_output_stream_report(JNIEnv *env);

void ibmp_byte_output_stream_init(JNIEnv *env);
void ibmp_byte_output_stream_end(JNIEnv *env);

#endif
