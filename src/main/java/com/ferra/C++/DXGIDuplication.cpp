// DXGIDuplicationDLL.cpp
#include <windows.h>
#include <dxgi1_2.h>
#include <d3d11.h>

#pragma comment(lib, "d3d11.lib")
#pragma comment(lib, "dxgi.lib")

extern "C" __declspec(dllexport) int captureDesktop(int width, int height, unsigned char* buffer) {
    static ID3D11Device* device = nullptr;
    static ID3D11DeviceContext* context = nullptr;
    static IDXGIOutputDuplication* duplication = nullptr;
    static ID3D11Texture2D* stagingTex = nullptr;

    if (!device) {
        D3D_FEATURE_LEVEL featureLevel;
        D3D11CreateDevice(nullptr, D3D_DRIVER_TYPE_HARDWARE, nullptr, 0, nullptr, 0,
                          D3D11_SDK_VERSION, &device, &featureLevel, &context);

        IDXGIDevice* dxgiDevice = nullptr;
        device->QueryInterface(__uuidof(IDXGIDevice), (void**)&dxgiDevice);
        IDXGIAdapter* adapter = nullptr;
        dxgiDevice->GetAdapter(&adapter);
        IDXGIOutput* output = nullptr;
        adapter->EnumOutputs(0, &output);

        IDXGIOutput1* output1 = nullptr;
        output->QueryInterface(__uuidof(IDXGIOutput1), (void**)&output1);

        output1->DuplicateOutput(device, &duplication);

        D3D11_TEXTURE2D_DESC desc = {};
        desc.Width = width;
        desc.Height = height;
        desc.MipLevels = 1;
        desc.ArraySize = 1;
        desc.Format = DXGI_FORMAT_B8G8R8A8_UNORM;
        desc.SampleDesc.Count = 1;
        desc.Usage = D3D11_USAGE_STAGING;
        desc.CPUAccessFlags = D3D11_CPU_ACCESS_READ;
        desc.BindFlags = 0;
        device->CreateTexture2D(&desc, nullptr, &stagingTex);

        dxgiDevice->Release();
        adapter->Release();
        output->Release();
        output1->Release();
    }

    DXGI_OUTDUPL_FRAME_INFO frameInfo;
    IDXGIResource* desktopResource = nullptr;
    HRESULT hr = duplication->AcquireNextFrame(16, &frameInfo, &desktopResource);
    if (FAILED(hr)) return 0;

    ID3D11Texture2D* desktopTex = nullptr;
    desktopResource->QueryInterface(__uuidof(ID3D11Texture2D), (void**)&desktopTex);
    context->CopyResource(stagingTex, desktopTex);

    D3D11_MAPPED_SUBRESOURCE mapped;
    context->Map(stagingTex, 0, D3D11_MAP_READ, 0, &mapped);

    memcpy(buffer, mapped.pData, width * height * 4);

    context->Unmap(stagingTex, 0);
    desktopTex->Release();
    desktopResource->Release();
    duplication->ReleaseFrame();

    return 1;
}
