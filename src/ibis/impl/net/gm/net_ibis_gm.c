// #define NDEBUG

#include <jni.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <assert.h>
#include <sched.h>
#include <gm.h>

#include "ibis_ipl_impl_net_gm_Driver.h"
#include "ibis_ipl_impl_net_gm_GmInput.h"
#include "ibis_ipl_impl_net_gm_GmOutput.h"


/*
 *  Macros
 */
#if 0
#define __RDTSC__ ({jlong time;__asm__ __volatile__ ("rdtsc" : "=A" (time));time;})
#else
#define __RDTSC__ 0LL
#endif

/* Debugging macros */
#if 0
#define __trace__(s, p...) fprintf(stderr, "[%Ld]:%s:%d: "s"\n", __RDTSC__, __FUNCTION__, __LINE__ , ## p)
#else
#define __trace__(s, p...)
#endif

#if 0
#define __disp__(s, p...) fprintf(stderr, "[%Ld]:%s:%d: "s"\n", __RDTSC__, __FUNCTION__, __LINE__ , ## p)
#define __in__()          fprintf(stderr, "[%Ld]:%s:%d: -->\n", __RDTSC__, __FUNCTION__, __LINE__)
#define __out__()         fprintf(stderr, "[%Ld]:%s:%d: <--\n", __RDTSC__, __FUNCTION__, __LINE__)
#define __err__()         fprintf(stderr, "[%Ld]:%s:%d: <!!\n", __RDTSC__, __FUNCTION__, __LINE__)
#else
#define __disp__(s, p...)
#define __in__() 
#define __out__()
#define __err__()
#endif

/* Error message macros */
#if 1
#define __error__(s, p...) fprintf(stderr, "[%Ld]:%s:%d: *error* "s"\n", __RDTSC__, __FUNCTION__, __LINE__ , ## p)
#else
#define __error__(s, p...)
#endif

/*
 *  Constants
 */

/* Cache settings */
#define CACHE_SIZE 10
#define CACHE_GRANULARITY 0x1000

/* The name of the Ibis exception for I/O related failures. */
#define NI_IBIS_EXCEPTION  "ibis.ipl.IbisIOException"

/* The minimal valid GM port ID.*/
#define NI_GM_MIN_PORT_NUM 2

/* The maximal atomic block size. */
#define NI_GM_MAX_BLOCK_LEN    (2*1024*1024)

#define NI_GM_PACKET_LEN       8

/*
 *  Types
 */

/* The driver */
struct s_drv {
	int            ref_count;
        int            nb_dev;
        struct s_dev **pp_dev;
};

#if 0
struct s_mutex {
        jobject   ref;
        jmethodID unlock_id;
};
#endif

struct s_lock {
        jobject   ref;
        jmethodID unlock_id;  
        jint      id;
};



struct s_request {
        struct s_port   *p_port;
        struct s_output *p_out;
        struct s_input  *p_in;
        gm_status_t      status;        
};

/* A Myricom NIC. */
struct s_dev {
	int            id;
        struct s_port *p_port;
	int            ref_count;
        struct s_drv  *p_drv;
};

/* A NIC port. */
struct s_port {
        struct s_cache   *cache_head;
	struct gm_port   *p_gm_port;
	int               port_id;
	unsigned int      node_id;
	struct s_dev     *p_dev;
	int               ref_count;

        struct s_input  **local_input_array;
        int               local_input_array_size;

        struct s_output **local_output_array;
        int               local_output_array_size;

        struct s_input   *active_input;

        unsigned char    *packet;
        int               packet_length;
        int               packet_size;
};

/* NetIbis output internal information. */
struct s_output {
        struct s_lock    *p_lock;
        struct s_cache   *p_cache;
        jbyteArray        j_byte_array;
        void             *byte_array;
        void             *buffer;
        int               length;
	struct s_port    *p_port;
	int               dst_port_id;
	unsigned int      dst_node_id;
        int               local_mux_id;
        int               remote_mux_id;
        unsigned char    *packet;
        int               packet_length;
        int               packet_size;
        int               state;
        struct s_request  request;
};

/* NetIbis input internal information. */
struct s_input {
        jobject           ref;
        jfieldID              len_id;
        struct   s_lock  *p_lock;
        struct   s_cache *p_cache;
        volatile int      data_available;
        jbyteArray        j_byte_array;
        void             *byte_array;
        void             *buffer;
        int               length;
	struct   s_port  *p_port;
	int               src_port_id;
	unsigned int      src_node_id;
        int               local_mux_id;
        int               remote_mux_id;
        unsigned char    *packet;
        int               packet_length;
        int               packet_size;
        struct s_request  request;
};

struct s_cache {
        void           *ptr;
        int             len;
        int             ref_count;
        struct s_cache *next;
};


/* Union used for conversions between pointers and handles. */
union u_conv {
        jlong  handle;
        void  *ptr;
};

/*
 *  Static variables
 */

/* Flag indicating whether the driver has been initialized. */
static int 	      initialized              =    0;

/* Flag indicating whether the initialization of GM was successful. */
static int 	      successfully_initialized =    0;

/* Driver's own data structure.  */
static struct s_drv * volatile _p_drv = NULL;

static JavaVM *_p_vm = NULL;
static JNIEnv *_current_env = NULL;

static const int pub_port_array[] = { 2, 4, 5, 6, 7 };
static const int nb_pub_ports   = 5;

/*
 *  Prototypes
 */



/*
 *  Functions
 */

/*
 * Convert a pointer to a 'jlong' handle.
 */
static
jlong
ni_gm_ptr2handle(void  *ptr) {
        union u_conv u;
        
        assert(sizeof(u.handle) >= sizeof(u.ptr));
        u.handle = 0;
        u.ptr    = ptr;

        return u.handle;
}

/*
 * Convert a 'jlong' handle to a pointer.
 */
static
void *
ni_gm_handle2ptr(jlong handle) {
        union u_conv u;
        
        assert(sizeof(u.handle) >= sizeof(u.ptr));
        u.handle = handle;

        return u.ptr;
}

/* GM error message display fonction. */
static
void
ni_gm_control(gm_status_t gm_status, int line)
{
	char *msg = NULL;

	switch (gm_status) {
	case GM_SUCCESS:
		break;

        case GM_FAILURE:
                msg = "GM failure";
                break;
                
        case GM_INPUT_BUFFER_TOO_SMALL:
                msg = "GM input buffer too small";
                break;
                        
        case GM_OUTPUT_BUFFER_TOO_SMALL:
                msg = "GM output buffer too small";
                break;
                
        case GM_TRY_AGAIN:
                msg = "GM try again";
                break;

        case GM_BUSY:
                msg = "GM busy";
                break;
                
        case GM_MEMORY_FAULT:
                msg = "GM memory fault";
                break;

        case GM_INTERRUPTED:
                msg = "GM interrupted";
                break;
                
        case GM_INVALID_PARAMETER:
                msg = "GM invalid parameter";
                break;
                
        case GM_OUT_OF_MEMORY:
                msg = "GM out of memory";
                break;
                
        case GM_INVALID_COMMAND:
                msg = "GM invalid command";
                break;

        case GM_PERMISSION_DENIED:
                msg = "GM permission denied";
                break;

        case GM_INTERNAL_ERROR:
                msg = "GM internal error";
                break;

        case GM_UNATTACHED:
                msg = "GM unattached";
                break;

        case GM_UNSUPPORTED_DEVICE:
                msg = "GM unsupported device";
                break;

        case GM_SEND_TIMED_OUT:
		msg = "GM send timed out";
                break;

        case GM_SEND_REJECTED:
		msg = "GM send rejected";
                break;

        case GM_SEND_TARGET_PORT_CLOSED:
		msg = "GM send target port closed";
                break;

        case GM_SEND_TARGET_NODE_UNREACHABLE:
		msg = "GM send target node unreachable";
                break;

        case GM_SEND_DROPPED:
		msg = "GM send dropped";
                break;

        case GM_SEND_PORT_CLOSED:
		msg = "GM send port closed";
                break;

        case GM_NODE_ID_NOT_YET_SET:
                msg = "GM id not yet set";
                break;

        case GM_STILL_SHUTTING_DOWN:
                msg = "GM still shutting down";
                break;

        case GM_CLONE_BUSY:
                msg = "GM clone busy";
                break;

        case GM_NO_SUCH_DEVICE:
                msg = "GM no such device";
                break;

        case GM_ABORTED:
                msg = "GM aborted";
                break;

#if GM_API_VERSION >= GM_API_VERSION_1_5
        case GM_INCOMPATIBLE_LIB_AND_DRIVER:
                msg = "GM incompatible lib and driver";
                break;

        case GM_UNTRANSLATED_SYSTEM_ERROR:
                msg = "GM untranslated system error";
                break;

        case GM_ACCESS_DENIED:
                msg = "GM access denied";
                break;
#endif

	default:
		msg = "unknown GM error";
		break;
	}

	if (msg) {
		fprintf(stderr, "%d:%s\n", line, msg);
                gm_perror ("gm_message", gm_status);
	}
}

static
int
ni_gm_lock_init(JNIEnv          *env,
                int              id,
                struct s_lock  **pp_lock) {
        struct s_lock *p_lock = NULL;

        __in__();
        p_lock = malloc(sizeof(struct s_lock));
        assert(p_lock);

        {
                jclass   driver_class = 0;
                jclass   lock_class   = 0;
                jfieldID fid          = 0;
                jobject  lock_array   = 0;

                driver_class      = (*env)->FindClass(env, "ibis/ipl/impl/net/gm/Driver");
                assert(driver_class);

                fid               = (*env)->GetStaticFieldID(env, driver_class, "gmLockArray", "Libis/ipl/impl/net/NetLockArray;");
                assert(fid);

                lock_array        = (*env)->GetStaticObjectField(env, driver_class, fid);
                assert(lock_array);
                
                p_lock->ref       = (*env)->NewGlobalRef(env, lock_array);
                assert(p_lock->ref);

                lock_class        = (*env)->FindClass(env, "ibis/ipl/impl/net/NetLockArray");
                assert(lock_class);

                p_lock->unlock_id = (*env)->GetMethodID(env, lock_class, "unlock", "(I)V");
                assert(p_lock->unlock_id);
        }
        
        p_lock->id = (jint)id;
        assert(!*pp_lock);
        *pp_lock = p_lock;
        __out__();

        return 0;
}

static
int 
ni_gm_lock_unlock(struct s_lock *p_lock) {
        JNIEnv *env = _current_env;
;
        
        __in__();
        if (!env) {
                (*_p_vm)->AttachCurrentThread(_p_vm, (void **)&env, NULL);
        }
        
        (*env)->CallVoidMethod(env, p_lock->ref, p_lock->unlock_id, p_lock->id);
        __out__();

        return 0;
}

static
int 
ni_gm_input_unlock(struct s_input *p_in, int len) {
        struct s_lock *p_lock = NULL;
        JNIEnv        *env    = _current_env;
        
        __in__();
        if (!env) {
                (*_p_vm)->AttachCurrentThread(_p_vm, (void **)&env, NULL);
        }        

        (*env)->SetIntField(env, p_in->ref, p_in->len_id, len);
        p_lock = p_in->p_lock;
        (*env)->CallVoidMethod(env, p_lock->ref, p_lock->unlock_id, p_lock->id);
        __out__();

        return 0;
}

#if 0
static
int 
ni_gm_mutex_init(JNIEnv          *env,
                 jobject          object,
                 char            *field,
                 struct s_mutex **pp_mutex) {
        struct s_mutex *p_mutex = NULL;

        __in__();
        p_mutex = malloc(sizeof(struct s_mutex));
        assert(p_mutex);

        {
                jclass   object_class = 0;
                jclass   mutex_class  = 0;
                jfieldID fid          = 0;
                jobject  mutex        = 0;
                
                object_class = (*env)->GetObjectClass(env, object);
                assert(object_class);
                
                fid = (*env)->GetFieldID(env, object_class, field,
                                         "Libis/ipl/impl/net/NetMutex;");
                assert(fid);
                
                mutex = (*env)->GetObjectField(env, object, fid);
                assert(mutex);
                
                p_mutex->ref = (*env)->NewGlobalRef(env, mutex);
                assert(p_mutex->ref);
                
                mutex_class = (*env)->FindClass(env, "ibis/ipl/impl/net/NetMutex");
                assert(mutex_class);
                
                p_mutex->unlock_id =
                        (*env)->GetMethodID(env, mutex_class, "unlock", "()V");
                assert(p_mutex->unlock_id);
        }

        assert(!*pp_mutex);
        *pp_mutex = p_mutex;
        __out__();

        return 0;
}

static
int 
ni_gm_mutex_unlock(struct s_mutex *p_mutex) {
        JNIEnv        *env    = _current_env;
        
        __in__();
        if (!env) {
                (*_p_vm)->AttachCurrentThread(_p_vm, (void **)&env, NULL);
        }        
        (*env)->CallVoidMethod(env, p_mutex->ref, p_mutex->unlock_id);
        __out__();

        return 0;
}
#endif

static
int 
ni_gm_release_byte_array(jbyteArray b, void *ptr) {
        JNIEnv        *env    = _current_env;
        
        __in__();
        if (!env) {
                (*_p_vm)->AttachCurrentThread(_p_vm, (void **)&env, NULL);
        }        
        (*env)->ReleaseByteArrayElements(env, b, ptr, 0);
        __out__();

        return 0;
}

static
int
ni_gm_check_send_tokens(struct s_port *p_port) {
        unsigned int nb_tokens = 0;

        __in__();
        nb_tokens = gm_num_send_tokens(p_port->p_gm_port);

        if (nb_tokens < 1) {
                __error__("no more send tokens");
                goto error;
        }
        
        __out__();
        return 0;
        
 error:
        __err__();
        return -1;
}

static
int
ni_gm_check_receive_tokens(struct s_port   *p_port) {
        unsigned int nb_tokens = 0;

        __in__();
        nb_tokens = gm_num_receive_tokens(p_port->p_gm_port);

        if (nb_tokens < 1) {
                __error__("no more receive tokens");
                goto error;
        }
        
        __out__();
        return 0;
        
 error:
        __err__();
        return -1;
}


static
int
ni_gm_register_block(struct s_port   *p_port,
                     void            *ptr,
                     int              len,
                     struct s_cache **_pp_cache) {
	struct gm_port *p_gm_port = p_port->p_gm_port;
        gm_status_t     gms       = GM_SUCCESS;
        struct s_cache  *p_cache  = NULL;
        struct s_cache **pp_cache = &p_port->cache_head;

        assert(ptr);
        assert(len);

        {
                unsigned long mask    = (CACHE_GRANULARITY - 1);
                unsigned long lng     = (long)ptr;
                unsigned long tmp_lng = (lng & ~mask);
                unsigned long offset  = lng - tmp_lng;

                ptr -= offset;
                len += offset;

                if (len & mask) {
                        len = (len & ~mask) + CACHE_GRANULARITY;
                }
        }        

        while ((p_cache = *pp_cache)) {
                if (ptr >= p_cache->ptr
                    &&
                    ptr+len <= p_cache->ptr+p_cache->len) {
                        p_cache->ref_count++;

                        if (pp_cache != &p_port->cache_head) {
                                /* Move the cache entry at the head */
                                *pp_cache = p_cache->next;
                                p_cache->next = p_port->cache_head;
                                p_port->cache_head = p_cache;
                        }

                        goto success;
                }

                pp_cache = &(p_cache->next);
        }

        p_cache = malloc(sizeof(struct s_cache));
        assert(p_cache);

        gms = gm_register_memory(p_gm_port, ptr, len);
        if (gms) {
                __error__("memory registration failed (ptr = %p, len = %d)", ptr, len);
                ni_gm_control(gms, __LINE__);
                free(p_cache);
                p_cache = NULL;
                goto error;
        }

        p_cache->ptr = ptr;
        p_cache->len = len;
        p_cache->ref_count = 1;
        p_cache->next = p_port->cache_head;
        p_port->cache_head = p_cache;
                
 success:
        *_pp_cache = p_cache;
        return 0;
        

 error:
        return -1;
}

static
int
ni_gm_deregister_block(struct s_port  *p_port, 
                       struct s_cache *p_cache) {
	struct gm_port *p_gm_port = p_port->p_gm_port;

        if (!--p_cache->ref_count) {
                gm_status_t     gms       = GM_SUCCESS;
                struct s_cache **pp_cache = &p_port->cache_head;
                int             i         = 0;

                while (*pp_cache != p_cache) {
                        pp_cache = &((*pp_cache)->next);
                        i++;
                }
                
                if (i >= CACHE_SIZE) {
                        gms = gm_deregister_memory(p_gm_port,
                                                   p_cache->ptr,
                                                   p_cache->len);
                        if (gms) {
                                __error__("memory deregistration failed");
                                ni_gm_control(gms, __LINE__);
                                goto error;
                        }

                        p_cache->ptr = NULL;
                        p_cache->len =    0;

                        *pp_cache = p_cache->next;
                        p_cache->next = NULL;

                        free(p_cache);
                }
        }

        return 0;
        
 error:
        return -1;
}


/* Opens a GM port and sets up a s_port structure for representing it. */
static
int
ni_gm_open_port(struct s_dev *p_dev) {
	struct s_port  *p_port    = NULL;
	struct gm_port *p_gm_port = NULL;
	gm_status_t     gms       = GM_SUCCESS;
	int             port_id   =  NI_GM_MIN_PORT_NUM;
        int             i         =  0;
	unsigned int    node_id   =  0;

	__in__();
#if 1
        while (i < nb_pub_ports) {
                port_id = pub_port_array[i];
                __disp__("trying to open GM port %d on device %d", port_id, p_dev->id);
        
                gms     = gm_open(&p_gm_port, p_dev->id, port_id,
                                  "net_ibis_gm", GM_API_VERSION_1_1);
                __disp__("status %d", gms);

                if (gms == GM_SUCCESS) {
                        __disp__("port ok");
                        goto found;
                }
                

                if (gms != GM_BUSY) {
                        __error__("gm_open failed");
                        ni_gm_control(gms, __LINE__);
                        goto error;
                }

                __disp__("port busy");
                i++;
        }
        
        __error__("no more GM port");
        goto error;
        
 found:
        ;
#else
        port_id = NI_GM_MIN_PORT_NUM;
        __disp__("opening GM port %d on device %d", port_id, p_dev->id);
        
	gms     = gm_open(&p_gm_port, p_dev->id, port_id,
			  "net_ibis_gm", GM_API_VERSION_1_1);
        __disp__("status %d", gms);
	if (gms != GM_SUCCESS) {
                __error__("gm_open failed");
		ni_gm_control(gms, __LINE__);
                goto error;
	}
#endif	

	p_port = malloc(sizeof(struct s_port));
        assert(p_port);

	gms = gm_get_node_id(p_gm_port, &node_id);
	if (gms != GM_SUCCESS) {
                __error__("gm_get_node_id failed");
		ni_gm_control(gms, __LINE__);
                goto error;
	}

        p_port->cache_head        = NULL;
	p_port->p_gm_port         = p_gm_port;
	p_port->port_id           = port_id;
	p_port->node_id           = node_id;
	p_port->p_dev             = p_dev;
	p_port->ref_count         = 0;

        p_port->local_input_array_size  = 0;
        p_port->local_input_array       = NULL;
        p_port->local_output_array_size = 0;
        p_port->local_output_array      = NULL;

        p_port->active_input = NULL;

        p_port->packet = gm_dma_malloc(p_gm_port, sizeof(NI_GM_PACKET_LEN));
        assert(p_port->packet);
        
        p_port->packet_length = NI_GM_PACKET_LEN;
        p_port->packet_size   = gm_min_size_for_length(p_port->packet_length);

        if (ni_gm_check_receive_tokens(p_port)) {
                goto error;
        }

        gm_provide_receive_buffer_with_tag(p_port->p_gm_port, p_port->packet,
                                           p_port->packet_size,
                                           GM_HIGH_PRIORITY, 1);

#if 1
        {
                const int nb = 16;
                int i = 0;
                /*
                 * The packet memory is never used actually, so we can use the same packet
                 * several times.
                 */
                while (i++ < nb && gm_num_receive_tokens(p_port->p_gm_port) > 1) {
                        gm_provide_receive_buffer_with_tag(p_port->p_gm_port, p_port->packet,
                                                           p_port->packet_size,
                                                           GM_HIGH_PRIORITY, 1);
                }
        }
#endif
        p_dev->p_port     = p_port;
	p_dev->ref_count++;
        __out__();
	return 0;

 error:
        __err__();
        return -1;
}
static
int
ni_gm_expand_array(void         **pp,
                   int           *ps,
                   int            i,
                   const size_t   o) {
        void *p = *pp;
        int   s = *ps;

        __in__();
        if (!p) {
                assert(!s);
                p = malloc(o);
                assert(p);
                memset(p, 0, o);
                s = 1;
        } else if (s <= i) {
                int ns = i + 1;

                p = realloc(p, ns * o);
                assert(p);
                memset(p+s*o, 0, (ns-s)*o);
                s = ns;
        }
        
        *pp = p;
        *ps = s;
	
        __out__();
	return 0;
}

static
void
ni_gm_callback(struct gm_port *port,
               void           *ptr,
               gm_status_t     gms) {
        struct s_request *p_rq   = NULL;
        struct s_port    *p_port = NULL;
        
        __in__();
        p_rq         = ptr;
        p_rq->status = gms;
        p_port       = p_rq->p_port;

        assert(p_rq->p_out || p_rq->p_in);

        if (p_rq->p_out) {
                struct s_output *p_out = NULL;

                assert(!p_rq->p_in);
                p_out = p_rq->p_out;

                assert(p_out->state == 1  ||  p_out->state == 3);
                
                if (p_out->state == 1) {
                        p_out->state = 2;
                        __disp__("ni_gm_callback: unlock(%d)\n", p_out->p_lock->id);
                        ni_gm_lock_unlock(p_out->p_lock);
                } else if (p_out->state == 3) {
                        ni_gm_deregister_block(p_port, p_out->p_cache);
                        ni_gm_release_byte_array(p_out->j_byte_array, p_out->byte_array);
                        p_out->p_cache = NULL;
                        p_out->buffer  = NULL;
                        p_out->length  = 0;
                        p_out->state   = 0;
                        __disp__("ni_gm_callback: unlock(%d)\n", p_out->p_lock->id);
                        ni_gm_lock_unlock(p_out->p_lock);
                } else {
                        abort();
                }
        } else if (p_rq->p_in) {
                struct s_input *p_in = NULL;

                assert(!p_rq->p_out);
                p_in = p_rq->p_in;
                __disp__("ni_gm_callback: unlock(%d)\n", p_in->p_lock->id);
                ni_gm_lock_unlock(p_in->p_lock);
        } else {
                abort();
        }
        
        __out__();
}

static
int
ni_gm_init_output(struct s_dev     *p_dev,
                  struct s_output **pp_out) {
        struct s_output *p_out  = NULL;
        struct s_port   *p_port = NULL;

        __in__();
        p_port = p_dev->p_port;

        p_out = malloc(sizeof(struct s_output));
        assert(p_out);

        p_out->p_port         = p_port;
        p_out->p_lock         = NULL;
        p_out->dst_port_id    = 0;
        p_out->dst_node_id    = 0;
        p_out->local_mux_id   = p_port->local_output_array_size;
        __disp__("%p: mux_id = %d\n", p_out, p_out->local_mux_id);
        
        p_out->remote_mux_id  = 0;

        ni_gm_expand_array((void **)&p_port->local_output_array,
                           &p_port->local_output_array_size,
                           p_out->local_mux_id,
                           sizeof(struct s_output *));
        assert(!p_port->local_output_array[p_out->local_mux_id]);
        p_out->packet = gm_dma_malloc(p_port->p_gm_port,
                                      sizeof(NI_GM_PACKET_LEN));
        assert(p_out->packet);
        p_out->packet_length = NI_GM_PACKET_LEN;
        p_out->packet_size   = gm_min_size_for_length(p_out->packet_length);

        p_out->state = 0;

        p_out->request.p_port = p_port;
        p_out->request.p_out  = p_out;
        p_out->request.p_in   = NULL;
        p_out->request.status =    0;

        p_port->local_output_array[p_out->local_mux_id] = p_out;
        p_port->ref_count++;

        *pp_out = p_out;
        __out__();
        
        return 0;
}

static
int
ni_gm_init_input(struct s_dev    *p_dev,
                 struct s_input **pp_in) {
        struct s_input *p_in  = NULL;
        struct s_port   *p_port = NULL;

        __in__();
        p_port = p_dev->p_port;

        p_in = malloc(sizeof(struct s_input));
        assert(p_in);

        p_in->p_lock         = NULL;
        p_in->data_available = 0;
        p_in->buffer         = NULL;
        p_in->length         = 0;
        p_in->p_port         = p_port;
        p_in->src_port_id    = 0;
        p_in->src_node_id    = 0;
        p_in->local_mux_id   = p_port->local_input_array_size;
        p_in->remote_mux_id  = 0;
        __disp__("%p: mux_id = %d\n", p_in, p_in->local_mux_id);

        ni_gm_expand_array((void **)&p_port->local_input_array,
                           &p_port->local_input_array_size,
                           p_in->local_mux_id,
                           sizeof(struct s_input *));
        assert(!p_port->local_input_array[p_in->local_mux_id]);
        p_in->packet = gm_dma_malloc(p_port->p_gm_port,
                                     sizeof(NI_GM_PACKET_LEN));
        assert(p_in->packet);
        p_in->packet_length = NI_GM_PACKET_LEN;
        p_in->packet_size   = gm_min_size_for_length(p_in->packet_length);

        p_in->request.p_port = p_port;
        p_in->request.p_out  = NULL;
        p_in->request.p_in   = p_in;
        p_in->request.status = 0;

        p_port->local_input_array[p_in->local_mux_id] = p_in;
        p_port->ref_count++;

        *pp_in = p_in;
        
        __out__(); 
        return 0;
}


static 
int 
ni_gm_get_output_node_id(struct s_output *p_out, int *p_id) {
        __in__();
        *p_id = p_out->p_port->node_id;
        __out__();
        return 0;
}        

static 
int 
ni_gm_get_input_node_id(struct s_input *p_in, int *p_id) {
        __in__();
        *p_id = p_in->p_port->node_id;
        __out__(); 
        return 0;
}        

static 
int 
ni_gm_get_output_port_id(struct s_output *p_out, int *p_id) {
        __in__();
        *p_id = p_out->p_port->port_id;
        __out__();
        return 0;
}        

static 
int 
ni_gm_get_input_port_id(struct s_input *p_in, int *p_id) {
        __in__();
        *p_id = p_in->p_port->port_id;
        __out__(); 
        return 0;
}        

static 
int 
ni_gm_get_output_mux_id(struct s_output *p_out, int *p_id) {
        __in__();
        *p_id = p_out->local_mux_id;
        __out__();
        return 0;
}        

static 
int 
ni_gm_get_input_mux_id(struct s_input *p_in, int *p_id) {
        __in__();
        *p_id = p_in->local_mux_id;
        __out__();
        return 0;
}        

static 
int 
ni_gm_connect_output(struct s_output *p_out,
                     int              remote_node_id,
                     int              remote_port_id,
                     int              remote_mux_id) {
        struct s_port *p_port = NULL;

        __in__();
        assert(!p_out->dst_node_id);
        assert(!p_out->dst_port_id);
        assert(!p_out->remote_mux_id);

        p_port = p_out->p_port;
        p_out->dst_node_id   = remote_node_id;
        p_out->dst_port_id   = remote_port_id;
        p_out->remote_mux_id = remote_mux_id;
        __disp__("ni_gm_connect_output: %d -> %d, lock = %d\n", p_out->local_mux_id, p_out->remote_mux_id, p_out->p_lock->id);

        p_out->packet[0] = 0;
        p_out->packet[1] = 0;
        p_out->packet[2] = 0;
        p_out->packet[3] = 0;

        p_out->packet[4] = (remote_mux_id >>  0) & 0xFF;
        p_out->packet[5] = (remote_mux_id >>  8) & 0xFF;
        p_out->packet[6] = (remote_mux_id >> 16) & 0xFF;
        p_out->packet[7] = (remote_mux_id >> 24) & 0xFF;
        __out__();

        return 0;
}

static 
int 
ni_gm_connect_input(struct s_input *p_in,
                    int             remote_node_id,
                    int             remote_port_id,
                    int             remote_mux_id) {
        struct s_port *p_port = NULL;

        __in__();
        assert(!p_in->src_node_id);
        assert(!p_in->src_port_id);
        assert(!p_in->remote_mux_id);

        p_port = p_in->p_port;

        p_in->src_node_id = remote_node_id;
        p_in->src_port_id = remote_port_id;
        p_in->remote_mux_id = remote_mux_id;
        __disp__("ni_gm_connect_input: %d <- %d, lock = %d\n", p_in->local_mux_id, p_in->remote_mux_id, p_in->p_lock->id);

        p_in->packet[0] = 1;
        p_in->packet[1] = 0;
        p_in->packet[2] = 0;
        p_in->packet[3] = 0;

        p_in->packet[4] = (remote_mux_id >>  0) & 0xFF;
        p_in->packet[5] = (remote_mux_id >>  8) & 0xFF;
        p_in->packet[6] = (remote_mux_id >> 16) & 0xFF;
        p_in->packet[7] = (remote_mux_id >> 24) & 0xFF;
        __out__(); 

        return 0;
}


static
int
ni_gm_output_send_request(struct s_output *p_out) {
        struct s_port    *p_port = NULL;
        struct s_request *p_rq   = NULL;

        __in__();
        assert(p_out->state == 0);
        p_out->state = 1;

        p_port = p_out->p_port;

        p_rq = &p_out->request;
        p_rq->status = 0;
        
        if (ni_gm_check_send_tokens(p_port)) {
                goto error;
        }

        gm_send_with_callback(p_port->p_gm_port, p_out->packet,
                              p_out->packet_size, p_out->packet_length,
                              GM_HIGH_PRIORITY,
                              p_out->dst_node_id, p_out->dst_port_id,
                              ni_gm_callback, p_rq);
        __out__();
        return 0;

 error:
        __err__();
        return -1;
}

static
int
ni_gm_output_send_buffer(struct s_output *p_out, void *b, int len) {
        struct s_port    *p_port = NULL;
        struct s_request *p_rq   = NULL;

        __in__();
        assert(b);
        assert(len);
        assert(p_out->state == 2);
        p_out->state = 3;

        p_port = p_out->p_port;
        ni_gm_register_block(p_port, b, len, &p_out->p_cache);
        p_out->buffer = b;
        p_out->length = len;
        p_rq = &p_out->request;
        p_rq->status = 0;
        
        if (ni_gm_check_send_tokens(p_port)) {
                goto error;
        }

        gm_send_with_callback(p_port->p_gm_port, b,
                              gm_min_size_for_length(NI_GM_MAX_BLOCK_LEN), len,
                              GM_LOW_PRIORITY,
                              p_out->dst_node_id, p_out->dst_port_id,
                              ni_gm_callback, p_rq);

        __out__(); 
        return 0;

 error:
        __err__();
        return -1;
}


static
int
ni_gm_input_post_buffer(struct s_input *p_in, void *b, int len) {
        struct s_port    *p_port = NULL;
        struct s_request *p_rq   = NULL;

        __in__();
        assert(b);
        assert(len);
        p_port = p_in->p_port;
        ni_gm_register_block(p_port, b, len, &p_in->p_cache);
        p_in->buffer = b;
        p_in->length = len;
        if (ni_gm_check_receive_tokens(p_port)) {
                goto error;
        }

        gm_provide_receive_buffer_with_tag(p_port->p_gm_port, b,
                                           gm_min_size_for_length(NI_GM_MAX_BLOCK_LEN),
                                           GM_LOW_PRIORITY, 0);

        p_port->active_input = p_in;

        p_rq = &p_in->request;
        p_rq->status = 0;

        if (ni_gm_check_send_tokens(p_port)) {
                goto error;
        }

        gm_send_with_callback(p_port->p_gm_port, p_in->packet,
                              p_in->packet_size, p_in->packet_length,
                              GM_HIGH_PRIORITY,
                              p_in->src_node_id, p_in->src_port_id,
                              ni_gm_callback, p_rq);
        __out__(); 
        return 0;

 error:
        __err__();
        return -1;
}


static
int
ni_gm_output_exit(struct s_output *p_out) {
        __in__();
        p_out->p_port->ref_count--;
        memset(p_out->p_lock, 0, sizeof(struct s_lock));
        free(p_out->p_lock);
        memset(p_out, 0, sizeof(struct s_output));
        free(p_out);
        
        __out__();
        return 0;
}

static int ni_gm_input_exit(struct s_input *p_in) {
        __in__();
        p_in->p_port->ref_count--;
        memset(p_in->p_lock, 0, sizeof(struct s_lock));
        free(p_in->p_lock);
        memset(p_in, 0, sizeof(struct s_input));
        free(p_in);
        __out__(); 
        
        return 0;
}


static
int
ni_gm_dev_init(JNIEnv        *env,
               struct s_drv  *p_drv,
               int            dev_num,
               struct s_dev **pp_dev) {
        struct s_dev *p_dev = NULL;
        
        *pp_dev = NULL;

        if (dev_num >= p_drv->nb_dev
            ||
            !p_drv->pp_dev[dev_num]) {

#if 0
                {        
                        struct gm_port *p_gm_port = NULL;
                        gm_status_t     gms       = GM_SUCCESS;

                        /* Try to open the first public port on requested device. */
                        gms = gm_open(&p_gm_port, dev_num, NI_GM_MIN_PORT_NUM,
                                      "net_ibis_gm", GM_API_VERSION_1_1);
                        if (gms != GM_SUCCESS) {
                                __error__("gm_open failed");
                                ni_gm_control(gms, __LINE__);
                                goto error;
                        }

                        gm_close(p_gm_port);
                        p_gm_port = NULL;
                }
#endif // 0            
                p_dev = malloc(sizeof(struct s_dev));
                if (!p_dev) {
                        __error__("memory allocation failed");
                        goto error;
                }
                
                if (!p_drv->nb_dev) {
                        p_drv->pp_dev = malloc(sizeof(struct s_dev *));
                        if (!p_drv->pp_dev) {
                                free(p_dev);
                                p_dev = NULL;

                                __error__("memory allocation failed");
                                goto error;
                        }

                        p_drv->nb_dev = 1;
                } else if (dev_num >= p_drv->nb_dev) {
                        p_drv->pp_dev = realloc(p_drv->pp_dev,
                                                dev_num * sizeof(struct s_dev *));
                        if (!p_drv->pp_dev) {
                                free(p_dev);
                                p_dev = NULL;

                                __error__("memory reallocation failed");
                                goto error;
                        }

                        while (p_drv->nb_dev + 1 < dev_num) {
                                p_drv->pp_dev[p_drv->nb_dev++] = NULL;
                        }

                        p_drv->nb_dev++;
                }
                
                p_dev->id        = dev_num;
                p_dev->ref_count = 0;
                p_dev->p_port    = NULL;
                p_dev->p_drv     = p_drv;

                if (ni_gm_open_port(p_dev)) {
                        __error__("port opening failed");
                        goto error;
                }

                p_drv->pp_dev[dev_num] = p_dev;
        } else {
                p_dev = p_drv->pp_dev[dev_num];
        }
        
        p_dev->ref_count++;
        *pp_dev = p_dev;

        __out__();
        return 0;

 error:
        __err__();
        return -1;
}

/* Frees a s_port structure and its associated GM port. */
static
int
ni_gm_close_port(struct s_port *p_port) {
        __in__();
	assert(!p_port->ref_count);
        
        while (p_port->cache_head) {
                if (ni_gm_deregister_block(p_port, p_port->cache_head)) {
                        __error__("block deregistration failed");
                        goto error;
                }
                
        }
        
	gm_close(p_port->p_gm_port);

        free(p_port->local_input_array);
        free(p_port->local_output_array);

        memset(p_port, 0, sizeof(struct s_port));
        free(p_port);
	
        __out__();
	return 0;

 error:
        __err__();
        return -1;
}

static
int
ni_gm_dev_exit(JNIEnv *env, struct s_dev *p_dev) {
        p_dev->ref_count--;

        if (!p_dev->ref_count) {
                if (ni_gm_close_port(p_dev->p_port)) {
                        __error__("port closing failed");
                        goto error;
                }

                p_dev->p_drv->ref_count--;

                memset(p_dev, 0, sizeof(struct s_dev));
                free(p_dev);
        }

        __out__();
        return 0;

 error:

        __err__();
        return -1;
}

static
int
ni_gm_init(struct s_drv **pp_drv) {
	struct s_drv *p_drv = NULL;
	gm_status_t   gms   = GM_SUCCESS;

        __in__();
	if (initialized) {
		return successfully_initialized;
	}

	gms = gm_init();
        
	if (gms != GM_SUCCESS) {
                __error__("gm_init failed");
		ni_gm_control(gms, __LINE__);
		goto error;
	}

	p_drv = malloc(sizeof(struct s_drv));
	if (!p_drv) {
                __error__("memory allocation failed");
		goto error;
        }
        
	p_drv->ref_count = 1;
        p_drv->nb_dev    = 0;
        p_drv->pp_dev    = NULL;

	*pp_drv = p_drv;
	initialized = 1;

        __out__();
	return 0;

 error:
        __err__();
        return -1;
}

static
int
ni_gm_exit(struct s_drv *p_drv) {
        __in__();
	if (!successfully_initialized)
		goto error;
	
	if (p_drv) {
                if (p_drv->ref_count > 1)
                        goto error;

                p_drv->ref_count = 0;
                p_drv->nb_dev    = 0;
                free(p_drv->pp_dev);
                p_drv->pp_dev    = NULL;
                
                free(p_drv);
                p_drv = NULL;

                gm_finalize();
        }

        successfully_initialized = 0;
        
        __out__();
	return 0;

 error:
        __err__();
        return -1;
}

/* ____________________________________________________________________________
 *
 *
 * Java Native Interfacing code
 *
 * ____________________________________________________________________________
 */
static 
void
ni_gm_throw_exception(JNIEnv *env,
                      char   *msg) {
        jclass cls = 0;
        
        cls = (*env)->FindClass(env, NI_IBIS_EXCEPTION);
        assert(cls);
        (*env)->ThrowNew(env, cls, msg);
}


/*
 * Class:     GmOutput
 * Method:    nInitOutput
 * Signature: (J)J
 */
JNIEXPORT
jlong
JNICALL
Java_ibis_ipl_impl_net_gm_GmOutput_nInitOutput(JNIEnv  *env,
                                                jobject  output,
                                                jlong    device_handle) {
        struct s_dev    *p_dev  = NULL;
        struct s_output *p_out  = NULL;
        jlong            result =    0;

        __in__();
        p_dev = ni_gm_handle2ptr(device_handle);
        ni_gm_init_output(p_dev, &p_out);
        ni_gm_lock_init(env, p_out->local_mux_id*2 + 1, &p_out->p_lock);
        result = ni_gm_ptr2handle(p_out);
        __out__();
        return result;
}

/*
 * Class:     GmInput
 * Method:    nInitInput
 * Signature: (J)J
 */
JNIEXPORT
jlong
JNICALL
Java_ibis_ipl_impl_net_gm_GmInput_nInitInput(JNIEnv  *env,
                                              jobject  input,
                                              jlong    device_handle) {
        struct s_dev   *p_dev  = NULL;
        struct s_input *p_in   = NULL;
        jlong           result =    0;

        __in__();
        p_dev = ni_gm_handle2ptr(device_handle);
        ni_gm_init_input(p_dev, &p_in);
        ni_gm_lock_init(env, p_in->local_mux_id*2 + 2, &p_in->p_lock);

        {
                jclass in_cls = 0;

                p_in->ref = (*env)->NewGlobalRef(env, input);
                assert(p_in->ref);

                in_cls    = (*env)->GetObjectClass(env, p_in->ref);
                assert(in_cls);
         
                p_in->len_id = (*env)->GetFieldID(env, in_cls, "blockLen", "I");
                assert(p_in->len_id);
        }
        
        result = ni_gm_ptr2handle(p_in);
        __out__();
        return result;
}

/*
 * Class:     GmOutput
 * Method:    nGetOutputNodeId
 * Signature: (J)I
 */
JNIEXPORT
jint 
JNICALL
Java_ibis_ipl_impl_net_gm_GmOutput_nGetOutputNodeId(JNIEnv  *env,
                                                    jobject  output,
                                                    jlong    output_handle){
        struct s_output *p_out  = NULL;
        int              result = 0;
        
        __in__();
        p_out = ni_gm_handle2ptr(output_handle);
        ni_gm_get_output_node_id(p_out, &result);
        __out__();

        return (jint)result;
}

/*
 * Class:     GmInput
 * Method:    nGetInputNodeId
 * Signature: (J)I
 */
JNIEXPORT 
jint
JNICALL 
Java_ibis_ipl_impl_net_gm_GmInput_nGetInputNodeId(JNIEnv  *env,
                                                   jobject  input,
                                                   jlong    input_handle) {
        struct s_input *p_in  = NULL;
        int             result = 0;

        __in__();
        p_in = ni_gm_handle2ptr(input_handle);
        ni_gm_get_input_node_id(p_in, &result);
        __out__(); 

        return (jint)result;
}

/*
 * Class:     GmOutput
 * Method:    nGetOutputPortId
 * Signature: (J)I
 */
JNIEXPORT 
jint 
JNICALL 
Java_ibis_ipl_impl_net_gm_GmOutput_nGetOutputPortId(JNIEnv  *env,
                                                     jobject  output,
                                                     jlong    output_handle){
        struct s_output *p_out  = NULL;
        int              result = 0;
        
        __in__();
        p_out = ni_gm_handle2ptr(output_handle);
        ni_gm_get_output_port_id(p_out, &result);

        __out__();
        return (jint)result;
}

/*
 * Class:     GmInput
 * Method:    nGetInputPortId
 * Signature: (J)I
 */
JNIEXPORT 
jint
JNICALL 
Java_ibis_ipl_impl_net_gm_GmInput_nGetInputPortId(JNIEnv  *env,
                                                   jobject  input,
                                                   jlong    input_handle) {
        struct s_input *p_in   = NULL;
        int             result = 0;
        
        __in__();
        p_in = ni_gm_handle2ptr(input_handle);
        ni_gm_get_input_port_id(p_in, &result);
        __out__(); 

        return (jint)result;
}

/*
 * Class:     GmOutput
 * Method:    nGetOutputMuxId
 * Signature: (J)I
 */
JNIEXPORT 
jint 
JNICALL 
Java_ibis_ipl_impl_net_gm_GmOutput_nGetOutputMuxId(JNIEnv  *env,
                                                   jobject  output,
                                                   jlong    output_handle){
        struct s_output *p_out  = NULL;
        int              result = 0;
        
        __in__();
        p_out = ni_gm_handle2ptr(output_handle);
        ni_gm_get_output_mux_id(p_out, &result);

        __out__();
        return (jint)result;
}

/*
 * Class:     GmInput
 * Method:    nGetInputMuxId
 * Signature: (J)I
 */
JNIEXPORT 
jint
JNICALL 
Java_ibis_ipl_impl_net_gm_GmInput_nGetInputMuxId(JNIEnv  *env,
                                                 jobject  input,
                                                 jlong    input_handle) {
        struct s_input *p_in   = NULL;
        int             result = 0;
        
        __in__();
        p_in = ni_gm_handle2ptr(input_handle);
        ni_gm_get_input_mux_id(p_in, &result);
        __out__(); 

        return (jint)result;
}

/*
 * Class:     GmOutput
 * Method:    nConnectOutput
 * Signature: (JIII)V
 */
JNIEXPORT
void
JNICALL 
Java_ibis_ipl_impl_net_gm_GmOutput_nConnectOutput(JNIEnv  *env,
                                                  jobject  output,
                                                  jlong    output_handle,
                                                  jint     remote_node_id,
                                                  jint     remote_port_id,
                                                  jint     remote_mux_id) {
        struct s_output *p_out = NULL;
        
        __in__();
        p_out = ni_gm_handle2ptr(output_handle);
        ni_gm_connect_output(p_out,
                             (int)remote_node_id,
                             (int)remote_port_id,
                             (int)remote_mux_id);
        __out__();
}

/*
 * Class:     GmInput
 * Method:    nConnectInput
 * Signature: (JIII)V
 */
JNIEXPORT 
void 
JNICALL 
Java_ibis_ipl_impl_net_gm_GmInput_nConnectInput(JNIEnv  *env,        
                                                jobject  input,
                                                jlong    input_handle,
                                                jint     remote_node_id,
                                                jint     remote_port_id,
                                                jint     remote_mux_id) {
        struct s_input *p_in = NULL;
        
        __in__();
        p_in = ni_gm_handle2ptr(input_handle);
        ni_gm_connect_input(p_in,
                            (int)remote_node_id,
                            (int)remote_port_id,
                            (int)remote_mux_id);
        __out__(); 
}

JNIEXPORT
void
JNICALL
Java_ibis_ipl_impl_net_gm_GmOutput_nSendRequest(JNIEnv     *env,
                                                jobject     output,
                                                jlong       output_handle) {
        struct s_output *p_out   = NULL;

        __in__();
        p_out = ni_gm_handle2ptr(output_handle);
        if (ni_gm_output_send_request(p_out)) {
                ni_gm_throw_exception(env, "could not send a request");
                goto error;
        } 

        __out__();
        return;

 error:
        __err__();
}


JNIEXPORT
void
JNICALL
Java_ibis_ipl_impl_net_gm_GmOutput_nSendBuffer(JNIEnv     *env,
                                               jobject     output,
                                               jlong       output_handle,
                                               jbyteArray  b,
                                               jint        offset, 
                                               jint        length) {
        struct s_output *p_out   = NULL;
        jboolean         is_copy = JNI_TRUE;
        void            *buffer  = NULL;

        __in__();
        p_out = ni_gm_handle2ptr(output_handle);

        buffer  = (*env)->GetByteArrayElements(env, b, &is_copy);

        assert(buffer);
        assert(length);
        
        if (!buffer) {
                ni_gm_throw_exception(env, "could not get array elements");
                goto error;
        }

        p_out->j_byte_array = b;
        p_out->byte_array   = buffer;
        buffer += offset;

        if (ni_gm_output_send_buffer(p_out, buffer, (int)length)) {
                ni_gm_throw_exception(env, "could not send a buffer");
                goto error;
        } 

        __out__();
        return;

 error:
        __err__();
}

JNIEXPORT 
void 
JNICALL 
Java_ibis_ipl_impl_net_gm_GmInput_nPostBuffer(JNIEnv     *env,
                                              jobject     input,
                                              jlong       input_handle,
                                              jbyteArray  b,
                                              jint        offset,
                                              jint        len) {
        struct s_input *p_in = NULL;
        jboolean         is_copy = JNI_TRUE;
        void            *buffer  = NULL;

        __in__();
        p_in = ni_gm_handle2ptr(input_handle);
        
        buffer = (*env)->GetByteArrayElements(env, b, &is_copy);

        assert(buffer);
        assert(len);
        
        if (!buffer) {
                ni_gm_throw_exception(env, "could not get array elements");
                goto error;
        }

        p_in->j_byte_array = b;
        p_in->byte_array   = buffer;

        buffer += offset;
        
        if (ni_gm_input_post_buffer(p_in, buffer, (int)len)) {
                ni_gm_throw_exception(env, "could not post a buffer");
                goto error;
        }

        __out__(); 
        return;
        
 error:
        __err__(); 
}

JNIEXPORT
void
JNICALL
Java_ibis_ipl_impl_net_gm_GmOutput_nCloseOutput(JNIEnv  *env,
                                                jobject  output,
                                                jlong    output_handle) {
        struct s_output *p_out = NULL;
        
        __in__();
        p_out = ni_gm_handle2ptr(output_handle);
        if (ni_gm_output_exit(p_out)) {
                ni_gm_throw_exception(env, "could not close output");
                goto error;
        }

        __out__();
        return;

 error:
        __err__();
}

JNIEXPORT
void
JNICALL
Java_ibis_ipl_impl_net_gm_GmInput_nCloseInput(JNIEnv *env, jobject input, jlong input_handle) {
        struct s_input *p_in = NULL;
        
        __in__();
        p_in = ni_gm_handle2ptr(input_handle);
        if (ni_gm_input_exit(p_in)) {
                ni_gm_throw_exception(env, "could not close input");
                goto error;
        }        

        __out__(); 
        return;
        
 error:
        __err__(); 
}

JNIEXPORT 
jlong 
JNICALL 
Java_ibis_ipl_impl_net_gm_Driver_nInitDevice(JNIEnv *env, jobject driver, jint device_num) {
        struct s_dev *p_dev  = NULL;
        jlong         result =    0;

        __in__();
        if (!_p_drv) {
                struct s_drv *p_drv = NULL;

                if (ni_gm_init(&p_drv))
                        goto error;

                successfully_initialized = 1;
                _p_drv = p_drv;
        }
        

        if (ni_gm_dev_init(env, _p_drv, (int)device_num, &p_dev)) {
                ni_gm_throw_exception(env, "GM device initialization failed");
                goto error;
        }

        result = ni_gm_ptr2handle(p_dev);
        __out__();
        return result;

 error:
        __err__();
        return result;
}

JNIEXPORT 
void 
JNICALL
Java_ibis_ipl_impl_net_gm_Driver_nCloseDevice(JNIEnv *env, jobject driver, jlong device_handle) {
        struct s_dev *p_dev  = NULL;

        __in__();
        p_dev = ni_gm_handle2ptr(device_handle);
        if (ni_gm_dev_exit(env, p_dev)) {
                ni_gm_throw_exception(env, "GM device closing failed");
        }
        if (!_p_drv->ref_count) {
                ni_gm_exit(_p_drv);
                _p_drv = NULL;
                initialized = 0;
        }        
        __out__();
}

JNIEXPORT
void
JNICALL
Java_ibis_ipl_impl_net_gm_Driver_nGmThread(JNIEnv *env, jclass driver_class) {
        static int next_dev = 0;
        _current_env = env;

        //__in__();
        while (1) {
                struct s_port   *p_port  = NULL;
                gm_recv_event_t *p_event = NULL;
                struct s_drv    *p_drv   = NULL;
                struct s_dev    *p_dev   = NULL;
                int              dev     = 0;
                
                if (!_p_drv->nb_dev) {
                        break;
                }

                /*__disp__("__poll__");*/
                
                p_drv = _p_drv;

                if (next_dev >= p_drv->nb_dev) {
                        next_dev = 0;
                        break;
                }      
                
                dev     = next_dev++;
                p_dev   = p_drv->pp_dev[dev];
                p_port  = p_dev->p_port;
                p_event = gm_receive(p_port->p_gm_port);
                //p_event = gm_blocking_receive(p_port->p_gm_port);
                
                switch (gm_ntohc(p_event->recv.type)) {
                        
                case GM_FAST_HIGH_PEER_RECV_EVENT:
                case GM_FAST_HIGH_RECV_EVENT:
                        {
                                int            code           =    0;
                                int            mux_id         =    0;
                                unsigned char *msg            = NULL;
                                unsigned char *packet         = NULL;
                                int            remote_node_id =    0;
                                
                                __disp__("gm_high_receive_event:-->");
                                packet = gm_ntohp(p_event->recv.buffer);
                                assert(packet == p_port->packet);

                                msg = gm_ntohp(p_event->recv.message);

                                code   |= (int)(((unsigned int)msg[0]) <<  0);
                                code   |= (int)(((unsigned int)msg[1]) <<  8);
                                code   |= (int)(((unsigned int)msg[2]) << 16);
                                code   |= (int)(((unsigned int)msg[3]) << 24);
                                mux_id |= (int)(((unsigned int)msg[4]) <<  0);
                                mux_id |= (int)(((unsigned int)msg[5]) <<  8);
                                mux_id |= (int)(((unsigned int)msg[6]) << 16);
                                mux_id |= (int)(((unsigned int)msg[7]) << 24);

                                assert(code == 0  ||  code == 1);
                                assert(mux_id >= 0);

                                if (ni_gm_check_receive_tokens(p_port)) {
                                        goto error;
                                }
                                gm_provide_receive_buffer_with_tag(p_port->p_gm_port,
                                                                   p_port->packet,
                                                                   p_port->packet_size,
                                                                   GM_HIGH_PRIORITY, 1);

                                remote_node_id =
                                        gm_ntohs(p_event->recv.sender_node_id);

                                if (code == 0) {
                                        // send flow control
                                        struct s_input *p_in = NULL;

                                        p_in = p_port->local_input_array[mux_id];
                                        assert(remote_node_id == p_in->src_node_id);
                                        __disp__("gm_high_receive_event: unlock(%d)\n", p_in->p_lock->id);
                                        ni_gm_lock_unlock(p_in->p_lock);

                                } else if (code == 1) {
                                        // receive flow control
                                        struct s_output *p_out = NULL;

                                        p_out = p_port->local_output_array[mux_id];
                                        assert(remote_node_id == p_out->dst_node_id);
                                        __disp__("gm_high_receive_event: unlock(%d)\n", p_out->p_lock->id);
                                        ni_gm_lock_unlock(p_out->p_lock);

                                } else {
                                        abort();
                                }
                                __disp__("gm_high_receive_event:<--");
                        }                        
                        break;
                        
                case GM_PEER_RECV_EVENT: 
                case GM_RECV_EVENT: 
                        {
                                struct s_input *p_in = NULL;
                                int            remote_node_id =    0;

                                __disp__("gm_receive_event:-->");
                                p_in = p_port->active_input;

                                remote_node_id = gm_ntohs(p_event->recv.sender_node_id);
                                assert(remote_node_id == p_in->src_node_id);

                                ni_gm_deregister_block(p_port, p_in->p_cache);
                                ni_gm_release_byte_array(p_in->j_byte_array, p_in->byte_array);
                                __disp__("gm_receive_event: unlock(%d)\n", p_in->p_lock->id);
                                ni_gm_input_unlock(p_in, (int)gm_ntohl(p_event->recv.length));
                                __disp__("gm_receive_event:<--");
                        }
                        break;
                        
                case GM_NO_RECV_EVENT:
                        break;
                        
                default:
                        gm_unknown(p_port->p_gm_port, p_event);
                        break;
                }
        }

        //__out__();
        _current_env = NULL;
        return;

 error:
        __err__();
        _current_env = NULL;
}


jint 
JNI_OnLoad(JavaVM *vm, void *reserved) {
        __in__();
        _p_vm = vm;

	/* JNI 1.2 should be enough for now */
        __out__();
	return JNI_VERSION_1_2;
}

void
JNI_OnUnload(JavaVM *vm, void *reserved) {
        __in__();
        _p_vm = NULL;
        __out__();
} 
